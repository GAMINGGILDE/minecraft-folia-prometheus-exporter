#!/usr/bin/env bash

set -Eeuo pipefail

server_directory="${1:?Usage: server-smoke-test.sh <server-directory>}"
activation_marker="FoliaPrometheusExporter started."
failure_pattern="(Could not load|Failed to load|Error occurred while (loading|enabling)|Could not pass event).*FoliaPrometheusExporter|Task .*FoliaPrometheusExporter.*exception|FoliaPrometheusExporter.*(Exception|ERROR|SEVERE)|Exception.*FoliaPrometheusExporter"
thread_failure_pattern="(AsyncCatcher|Thread (check failed|failed main thread check)|Cannot getEntities asynchronously|Cannot getLoadedChunks asynchronously|not (the )?tick thread|not owned by (the )?current region|IllegalStateException:.*(region|thread)|[[:space:]]at .*TickThread|((Region|Entity) Scheduler.*(exception|failed|error))|((exception|failed|error).*(Region|Entity) Scheduler))"
event_failure_pattern="Could not pass event|EventException|IllegalPluginAccessException|(Unable|Failed|Could not) to register event|duplicate(d)? listener|listener.*already registered|(deprecated|deprecation).*(exception|error|fail)|(exception|error|fail).*(deprecated|deprecation)"
linkage_failure_pattern="(NoClassDefFoundError|ClassNotFoundException|LinkageError).*(FoliaPrometheusExporter|de\\.minecraftgilde\\.prometheus)|(FoliaPrometheusExporter|de\\.minecraftgilde\\.prometheus).*(NoClassDefFoundError|ClassNotFoundException|LinkageError)"
folia_unsupported_warning="The Folia collector is enabled, but the public Server#getRegionTPS(World,int,int) capability is unavailable; the collector is unsupported and no Folia metrics will be registered."
startup_timeout_seconds=240
shutdown_timeout_seconds=120
snapshot_timeout_seconds=90
entity_timeout_seconds=90
server_pid=""

server_directory="$(cd "$server_directory" && pwd)"
server_log="$server_directory/server.log"

if [[ ! -f "$server_directory/server.jar" ]]; then
  echo "Server JAR is missing: $server_directory/server.jar" >&2
  exit 1
fi

if [[ ! -f "$server_directory/plugins/FoliaPrometheusExporter.jar" ]]; then
  echo "Plugin JAR is missing from the server plugin directory." >&2
  exit 1
fi

cleanup() {
  cleanup_exit_code=$?

  if [[ -n "$server_pid" ]] && kill -0 "$server_pid" 2>/dev/null; then
    printf 'stop\n' >&3 2>/dev/null || true
    for ((attempt = 0; attempt < 30; attempt++)); do
      if ! kill -0 "$server_pid" 2>/dev/null; then
        break
      fi
      sleep 1
    done

    if kill -0 "$server_pid" 2>/dev/null; then
      kill -TERM "$server_pid" 2>/dev/null || true
    fi
    wait "$server_pid" 2>/dev/null || true
  fi

  exec 3>&- 2>/dev/null || true

  if [[ $cleanup_exit_code -ne 0 ]] && [[ -f "$server_log" ]]; then
    echo "Last 200 server log lines:" >&2
    tail -n 200 "$server_log" >&2 || true
  fi
}
trap cleanup EXIT

coproc SERVER_PROCESS {
  cd "$server_directory"
  exec java -Xms512M -Xmx1G -jar server.jar --nogui >"$server_log" 2>&1
}
server_pid=$SERVER_PROCESS_PID
exec 3>&"${SERVER_PROCESS[1]}"

activation_found=false
startup_deadline=$((SECONDS + startup_timeout_seconds))

while ((SECONDS < startup_deadline)); do
  if grep -Fq "$activation_marker" "$server_log" 2>/dev/null; then
    activation_found=true
    break
  fi

  if grep -Eiq "$failure_pattern" "$server_log" 2>/dev/null; then
    echo "Plugin load or activation failure detected." >&2
    grep -Ein "$failure_pattern" "$server_log" >&2 || true
    exit 1
  fi

  if grep -Eiq "$event_failure_pattern" "$server_log" 2>/dev/null; then
    echo "Event registration, listener, handler, or deprecation runtime failure detected." >&2
    grep -Ein "$event_failure_pattern" "$server_log" >&2 || true
    exit 1
  fi

  if grep -Eiq "$linkage_failure_pattern" "$server_log" 2>/dev/null; then
    echo "Plugin class-loading or linkage failure detected." >&2
    grep -Ein "$linkage_failure_pattern" "$server_log" >&2 || true
    exit 1
  fi

  if ! kill -0 "$server_pid" 2>/dev/null; then
    wait "$server_pid" || true
    server_pid=""
    echo "Server exited before the plugin activation marker appeared." >&2
    exit 1
  fi

  sleep 1
done

if [[ "$activation_found" != true ]]; then
  echo "Timed out after ${startup_timeout_seconds}s waiting for: $activation_marker" >&2
  exit 1
fi

if grep -Eiq "$failure_pattern" "$server_log"; then
  echo "Plugin failure was logged during activation." >&2
  grep -Ein "$failure_pattern" "$server_log" >&2 || true
  exit 1
fi

if grep -Eiq "$event_failure_pattern" "$server_log"; then
  echo "Event registration, listener, handler, or deprecation runtime failure was logged during activation." >&2
  grep -Ein "$event_failure_pattern" "$server_log" >&2 || true
  exit 1
fi

if grep -Eiq "$linkage_failure_pattern" "$server_log"; then
  echo "Plugin class-loading or linkage failure was logged during activation." >&2
  grep -Ein "$linkage_failure_pattern" "$server_log" >&2 || true
  exit 1
fi

exporter_base_url="http://127.0.0.1:9940"
health_response="$(curl --fail --silent --show-error --max-time 10 \
  "$exporter_base_url/health")"
ready_response="$(curl --fail --silent --show-error --max-time 10 \
  "$exporter_base_url/ready")"
if [[ "$health_response" != "ok" ]]; then
  echo "Unexpected /health response: $health_response" >&2
  exit 1
fi

metrics_response=""
snapshot_deadline=$((SECONDS + snapshot_timeout_seconds))
while ((SECONDS < snapshot_deadline)); do
  metrics_response="$(curl --fail --silent --show-error --max-time 10 \
    "$exporter_base_url/metrics")"
  if grep -Eq '^minecraft_server_info\{' <<<"$metrics_response" \
    && grep -Eq '^minecraft_players_online[[:space:]]' <<<"$metrics_response" \
    && grep -Eq '^minecraft_plugins_total[[:space:]]' <<<"$metrics_response" \
    && grep -Eq '^minecraft_world_players\{world="[^"]+"\}' <<<"$metrics_response" \
    && grep -Eq '^minecraft_world_loaded_chunks\{world="[^"]+"\}' <<<"$metrics_response" \
    && grep -Eq '^minecraft_world_size_bytes\{world="[^"]+"\}' <<<"$metrics_response" \
    && grep -Eq '^minecraft_entity_group_count\{group="[^"]+",world="[^"]+"\}|^minecraft_entity_group_count\{world="[^"]+",group="[^"]+"\}' <<<"$metrics_response" \
    && grep -Eq '^minecraft_world_entities\{world="[^"]+"\}' <<<"$metrics_response"; then
    break
  fi
  sleep 1
done

if [[ "$ready_response" != "ready" ]]; then
  echo "Unexpected /ready response: $ready_response" >&2
  exit 1
fi

entity_group_value() {
  local world="$1"
  local group="$2"
  awk -v expected_world="$world" -v expected_group="$group" '
    /^minecraft_entity_group_count\{/ {
      if (index($1, "world=\"" expected_world "\"") > 0 \
          && index($1, "group=\"" expected_group "\"") > 0) {
        print $2
        exit
      }
    }
  ' <<<"$metrics_response"
}

refresh_metrics() {
  metrics_response="$(curl --fail --silent --show-error --max-time 10 \
    "$exporter_base_url/metrics")"
}

smoke_world="world"
smoke_group="other"
printf '%s\n' 'forceload add 0 0' >&3

stable_value=""
stable_reads=0
stability_deadline=$((SECONDS + 30))
while ((SECONDS < stability_deadline)); do
  refresh_metrics
  current_value="$(entity_group_value "$smoke_world" "$smoke_group")"
  if [[ -n "$current_value" ]] && [[ "$current_value" == "$stable_value" ]]; then
    stable_reads=$((stable_reads + 1))
  else
    stable_value="$current_value"
    stable_reads=1
  fi
  if [[ -n "$stable_value" ]] && [[ "$stable_reads" -ge 3 ]]; then
    break
  fi
  sleep 1
done

if [[ -z "$stable_value" ]] || [[ "$stable_reads" -lt 3 ]]; then
  echo "Could not establish a stable entity baseline for world '$smoke_world'." >&2
  exit 1
fi

baseline_entity_group_value="$stable_value"
printf '%s\n' 'execute in minecraft:overworld run summon minecraft:area_effect_cloud 0 100 0 {Duration:200,WaitTime:0,Radius:0.1f,Tags:["folia_prometheus_smoke"]}' >&3

spawn_observed=false
entity_deadline=$((SECONDS + entity_timeout_seconds))
while ((SECONDS < entity_deadline)); do
  refresh_metrics
  current_value="$(entity_group_value "$smoke_world" "$smoke_group")"
  if [[ -n "$current_value" ]] && awk \
    -v current="$current_value" \
    -v baseline="$baseline_entity_group_value" \
    'BEGIN { exit ((current + 0) >= (baseline + 1)) ? 0 : 1 }'; then
    spawned_entity_group_value="$current_value"
    spawn_observed=true
    break
  fi
  sleep 1
done

if [[ "$spawn_observed" != true ]]; then
  echo "The controlled area-effect cloud was not observed in the 'other' entity group." >&2
  exit 1
fi

removal_observed=false
entity_deadline=$((SECONDS + entity_timeout_seconds))
while ((SECONDS < entity_deadline)); do
  refresh_metrics
  current_value="$(entity_group_value "$smoke_world" "$smoke_group")"
  if [[ -n "$current_value" ]] && awk \
    -v current="$current_value" \
    -v spawned="$spawned_entity_group_value" \
    'BEGIN { exit ((current + 0) < (spawned + 0)) ? 0 : 1 }'; then
    removal_observed=true
    break
  fi
  sleep 1
done

printf '%s\n' 'forceload remove 0 0' >&3

if [[ "$removal_observed" != true ]]; then
  echo "The controlled area-effect cloud expiration was not reflected in entity metrics." >&2
  exit 1
fi

echo "Controlled non-player entity addition and removal confirmed."

for required_metric in \
  minecraft_exporter_build_info \
  minecraft_exporter_ready \
  minecraft_exporter_scrapes_total \
  jvm_memory_used_bytes \
  jvm_threads_current \
  jvm_classes_currently_loaded \
  process_start_time_seconds \
  minecraft_server_info \
  minecraft_server_uptime_seconds \
  minecraft_server_start_time_seconds \
  minecraft_players_online \
  minecraft_players_known_total \
  minecraft_players_by_gamemode \
  minecraft_plugins_total \
  minecraft_plugins_enabled \
  minecraft_world_players \
  minecraft_world_loaded_chunks \
  minecraft_world_size_bytes \
  minecraft_world_time_ticks \
  minecraft_world_weather \
  minecraft_world_difficulty \
  minecraft_world_environment \
  minecraft_world_pvp_enabled \
  minecraft_login_attempts_total \
  minecraft_login_denied_total \
  minecraft_player_joins_total \
  minecraft_player_quits_total \
  minecraft_player_kicks_total \
  minecraft_server_list_pings_total \
  minecraft_chat_messages_total \
  minecraft_chunks_loaded_total \
  minecraft_chunks_unloaded_total \
  minecraft_chunks_generated_total \
  minecraft_entity_group_count \
  minecraft_world_entities \
  minecraft_world_living_entities \
  minecraft_world_villagers \
  minecraft_world_item_entities \
  minecraft_entity_reconciliation_duration_seconds \
  minecraft_entity_reconciliation_last_success_timestamp_seconds \
  minecraft_entity_reconciliation_corrections_total; do
  if ! grep -Fq "$required_metric" <<<"$metrics_response"; then
    echo "Missing exporter metric: $required_metric" >&2
    exit 1
  fi
done

for event_metric in \
  minecraft_login_attempts_total \
  minecraft_login_denied_total \
  minecraft_player_joins_total \
  minecraft_player_quits_total \
  minecraft_player_kicks_total \
  minecraft_server_list_pings_total \
  minecraft_chat_messages_total \
  minecraft_chunks_loaded_total \
  minecraft_chunks_unloaded_total \
  minecraft_chunks_generated_total; do
  if ! grep -Eq "^# HELP ${event_metric} " <<<"$metrics_response" \
    || ! grep -Eq "^# TYPE ${event_metric} counter$" <<<"$metrics_response"; then
    echo "Invalid or incomplete event Prometheus family: $event_metric" >&2
    exit 1
  fi
done

if ! grep -Eq '^minecraft_exporter_collector_state\{collector="events",state="running"\}[[:space:]]1' <<<"$metrics_response"; then
  echo "Event collector is not running under the default configuration." >&2
  exit 1
fi

if ! grep -Eq '^minecraft_exporter_collector_state\{collector="entities",state="running"\}[[:space:]]1' <<<"$metrics_response"; then
  echo "Entity collector is not running under the default configuration." >&2
  exit 1
fi

for entity_gauge in \
  minecraft_entity_group_count \
  minecraft_world_entities \
  minecraft_world_living_entities \
  minecraft_world_villagers \
  minecraft_world_item_entities \
  minecraft_entity_reconciliation_duration_seconds \
  minecraft_entity_reconciliation_last_success_timestamp_seconds; do
  if ! grep -Eq "^# HELP ${entity_gauge} " <<<"$metrics_response" \
    || ! grep -Eq "^# TYPE ${entity_gauge} gauge$" <<<"$metrics_response" \
    || ! grep -Eq "^${entity_gauge}(\\{|[[:space:]])" <<<"$metrics_response"; then
    echo "Invalid or incomplete entity Prometheus family: $entity_gauge" >&2
    exit 1
  fi
done

if ! grep -Eq '^# HELP minecraft_entity_reconciliation_corrections_total ' <<<"$metrics_response" \
  || ! grep -Eq '^# TYPE minecraft_entity_reconciliation_corrections_total counter$' <<<"$metrics_response"; then
  echo "Invalid entity reconciliation corrections counter." >&2
  exit 1
fi

group_count="$(grep -E '^minecraft_entity_group_count\{' <<<"$metrics_response" | wc -l | tr -d ' ')"
world_count="$(grep -E '^minecraft_world_entities\{' <<<"$metrics_response" | wc -l | tr -d ' ')"
if [[ "$world_count" -lt 1 ]] || [[ "$group_count" -ne $((world_count * 10)) ]]; then
  echo "Every captured world must expose exactly ten bounded entity groups." >&2
  exit 1
fi

for snapshot_metric in \
  minecraft_server_uptime_seconds \
  minecraft_players_online \
  minecraft_plugins_total \
  minecraft_world_players \
  minecraft_world_loaded_chunks \
  minecraft_world_size_bytes \
  minecraft_world_time_ticks \
  minecraft_world_weather; do
  if ! grep -Eq "^# HELP ${snapshot_metric} " <<<"$metrics_response" \
    || ! grep -Eq "^# TYPE ${snapshot_metric} gauge$" <<<"$metrics_response" \
    || ! grep -Eq "^${snapshot_metric}(\\{|[[:space:]])" <<<"$metrics_response"; then
    echo "Invalid or incomplete snapshot Prometheus family: $snapshot_metric" >&2
    exit 1
  fi
done

if ! grep -Eq '^# HELP minecraft_server_info ' <<<"$metrics_response" \
  || ! grep -Eq '^minecraft_server_info\{implementation="[^"]+",java_version="[^"]+",minecraft_version="[^"]+"\}[[:space:]]1' <<<"$metrics_response"; then
  echo "Server info metric is missing required labels or sample." >&2
  exit 1
fi

if grep -Fq 'minecraft_plugin_info' <<<"$metrics_response"; then
  echo "Optional plugin-info metric is unexpectedly enabled by default." >&2
  exit 1
fi

for optional_entity_metric in \
  minecraft_entities \
  minecraft_world_projectiles; do
  if grep -Fq "$optional_entity_metric" <<<"$metrics_response"; then
    echo "Optional entity metric is unexpectedly enabled by default: $optional_entity_metric" >&2
    exit 1
  fi
done

for forbidden_prefix in \
  minecraft_commands_total \
  minecraft_chunk_load_failures_total \
  minecraft_entities_spawned_total \
  minecraft_entities_removed_total \
  minecraft_mobs_killed_total \
  minecraft_items_despawned_total \
  minecraft_folia_active_regions \
  minecraft_folia_region_tick_duration_seconds \
  minecraft_folia_overloaded_regions \
  minecraft_folia_region_tick_delay_seconds; do
  if grep -Fq "$forbidden_prefix" <<<"$metrics_response"; then
    echo "Out-of-scope metric unexpectedly exposed: $forbidden_prefix" >&2
    exit 1
  fi
done

if [[ "${EXPECTED_PLATFORM:-}" == "Paper" ]]; then
  if grep -Fq 'minecraft_folia_' <<<"$metrics_response"; then
    echo "Paper unexpectedly exposed Folia metric families." >&2
    exit 1
  fi
  if ! grep -Eq '^minecraft_exporter_collector_state\{collector="folia",state="unsupported"\}[[:space:]]1' <<<"$metrics_response"; then
    echo "Paper Folia collector is not in the unsupported state." >&2
    exit 1
  fi
  warning_count="$(grep -Fc "$folia_unsupported_warning" "$server_log" || true)"
  if [[ "$warning_count" -ne 1 ]]; then
    echo "Expected exactly one Folia capability warning on Paper, found $warning_count." >&2
    grep -Fn "$folia_unsupported_warning" "$server_log" >&2 || true
    exit 1
  fi
elif [[ "${EXPECTED_PLATFORM:-}" == "Folia" ]]; then
  if ! grep -Eq '^minecraft_exporter_collector_state\{collector="folia",state="running"\}[[:space:]]1' <<<"$metrics_response"; then
    echo "Folia collector did not reach running state." >&2
    exit 1
  fi
  if grep -Fq "$folia_unsupported_warning" "$server_log"; then
    echo "Folia logged the Paper-only capability warning." >&2
    exit 1
  fi
  if grep -Eq '^minecraft_folia_.*[[:space:]](NaN|[+-]Inf|-([0-9]|\.))' <<<"$metrics_response"; then
    echo "Folia exposed a non-finite or negative region sample." >&2
    exit 1
  fi
  unexpected_folia_labels="$(
    grep -E '^minecraft_folia_[a-z0-9_]+\{' <<<"$metrics_response" \
      | grep -Eo '[,{][a-zA-Z_][a-zA-Z0-9_]*="' \
      | sed -E 's/^[,{]//; s/="$//' \
      | grep -Ev '^(world|window|stat|threshold)$' || true
  )"
  if [[ -n "$unexpected_folia_labels" ]]; then
    echo "Folia exposed an unexpected or identifying label: $unexpected_folia_labels" >&2
    exit 1
  fi
  if awk '
    /^minecraft_folia_observed_regions\{/ && ($NF + 0) == 0 { found = 1 }
    END { exit found ? 0 : 1 }
  ' <<<"$metrics_response"; then
    echo "Folia exposed an invented zero-valued observed-region sample." >&2
    exit 1
  fi
  if awk '
    /^minecraft_folia_observed_regions\{/ && ($NF + 0) > 0 { found = 1 }
    END { exit found ? 0 : 1 }
  ' <<<"$metrics_response"; then
    for folia_metric in \
      minecraft_folia_observed_regions \
      minecraft_folia_region_tps \
      minecraft_folia_regions_below_tps \
      minecraft_folia_regions_with_players \
      minecraft_folia_players_per_region \
      minecraft_folia_region_snapshot_age_seconds; do
      if ! grep -Eq "^# HELP ${folia_metric} " <<<"$metrics_response" \
        || ! grep -Eq "^# TYPE ${folia_metric} gauge$" <<<"$metrics_response" \
        || ! grep -Eq "^${folia_metric}\\{" <<<"$metrics_response"; then
        echo "Observed Folia regions require a complete metric family: $folia_metric" >&2
        exit 1
      fi
    done
  fi
fi

if grep -Eiq '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' <<<"$metrics_response"; then
  echo "A UUID-like value appeared in the metrics output." >&2
  exit 1
fi

if grep -Eiq "$thread_failure_pattern" "$server_log"; then
  echo "Paper/Folia thread ownership failure was logged." >&2
  grep -Ein "$thread_failure_pattern" "$server_log" >&2 || true
  exit 1
fi

if grep -Eiq "$event_failure_pattern" "$server_log"; then
  echo "Event registration, listener, handler, or deprecation runtime failure was logged." >&2
  grep -Ein "$event_failure_pattern" "$server_log" >&2 || true
  exit 1
fi

if grep -Eiq "$linkage_failure_pattern" "$server_log"; then
  echo "Plugin class-loading or linkage failure was logged before shutdown completed." >&2
  grep -Ein "$linkage_failure_pattern" "$server_log" >&2 || true
  exit 1
fi

for runtime_metric in \
  jvm_memory_used_bytes \
  jvm_threads_current \
  jvm_classes_currently_loaded \
  process_start_time_seconds; do
  if ! grep -Eq "^# HELP ${runtime_metric} " <<<"$metrics_response" \
    || ! grep -Eq "^# TYPE ${runtime_metric} (counter|gauge|summary)$" <<<"$metrics_response" \
    || ! grep -Eq "^${runtime_metric}(\\{|[[:space:]])" <<<"$metrics_response"; then
    echo "Invalid or incomplete Prometheus family: $runtime_metric" >&2
    exit 1
  fi
done

if [[ -n "${EXPECTED_GIT_COMMIT:-}" ]] \
  && ! grep -Fq "git_commit=\"${EXPECTED_GIT_COMMIT}\"" <<<"$metrics_response"; then
  echo "Exporter build_info does not contain expected Git commit ${EXPECTED_GIT_COMMIT}." >&2
  exit 1
fi

echo "Exporter HTTP endpoints confirmed."
echo "Plugin activation confirmed; requesting controlled server shutdown."
printf 'stop\n' >&3

shutdown_deadline=$((SECONDS + shutdown_timeout_seconds))
while kill -0 "$server_pid" 2>/dev/null && ((SECONDS < shutdown_deadline)); do
  sleep 1
done

if kill -0 "$server_pid" 2>/dev/null; then
  echo "Server did not stop within ${shutdown_timeout_seconds}s after the stop command." >&2
  exit 1
fi

set +e
wait "$server_pid"
server_exit_code=$?
set -e
server_pid=""

if [[ $server_exit_code -ne 0 ]]; then
  echo "Server exited with code $server_exit_code after the stop command." >&2
  exit 1
fi

if grep -Eiq "$failure_pattern" "$server_log"; then
  echo "Plugin failure was logged before shutdown completed." >&2
  grep -Ein "$failure_pattern" "$server_log" >&2 || true
  exit 1
fi

if grep -Eiq "$thread_failure_pattern" "$server_log"; then
  echo "Paper/Folia thread ownership failure was logged before shutdown completed." >&2
  grep -Ein "$thread_failure_pattern" "$server_log" >&2 || true
  exit 1
fi

if grep -Eiq "$event_failure_pattern" "$server_log"; then
  echo "Event registration, listener, handler, or deprecation runtime failure was logged before shutdown completed." >&2
  grep -Ein "$event_failure_pattern" "$server_log" >&2 || true
  exit 1
fi

if grep -Eiq "$linkage_failure_pattern" "$server_log"; then
  echo "Plugin class-loading or linkage failure was logged before shutdown completed." >&2
  grep -Ein "$linkage_failure_pattern" "$server_log" >&2 || true
  exit 1
fi

if curl --silent --max-time 2 "$exporter_base_url/health" >/dev/null 2>&1; then
  echo "Exporter HTTP server still responds after plugin shutdown." >&2
  exit 1
fi

grep -F "$activation_marker" "$server_log"
echo "Server stopped cleanly."
