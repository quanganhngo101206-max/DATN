#!/bin/bash
set -euo pipefail

SQLCMD=(/opt/mssql-tools18/bin/sqlcmd -S mssql -U sa -P "$MSSQL_SA_PASSWORD" -C -I -b)
INIT_SQL="/init/SkySport_merged.sql"
STATISTICS_SEED_SQL="/init/statistics_seed.sql"

echo "Waiting for SQL Server..."
for i in $(seq 1 60); do
  if "${SQLCMD[@]}" -Q "SELECT 1" &>/dev/null; then
    break
  fi
  sleep 2
done

ALREADY="$("${SQLCMD[@]}" -h -1 -W -Q "SET NOCOUNT ON; SELECT CASE WHEN OBJECT_ID('SkySport.dbo.Account') IS NULL THEN 0 ELSE 1 END" | tr -d '[:space:]' || echo 0)"
BILL_OK="$("${SQLCMD[@]}" -h -1 -W -Q "SET NOCOUNT ON; SELECT CASE WHEN OBJECT_ID('SkySport.dbo.Bill') IS NULL THEN 0 ELSE 1 END" | tr -d '[:space:]' || echo 0)"

if [ "$ALREADY" = "1" ] && [ "$BILL_OK" = "1" ] && [ "${FORCE_IMPORT:-0}" != "1" ]; then
  echo "SkySport already has schema/data — refreshing statistics demo data..."
  "${SQLCMD[@]}" -i "$STATISTICS_SEED_SQL"
  echo "Statistics demo data refreshed."
  exit 0
fi

if [ "$ALREADY" = "1" ]; then
  echo "Incomplete schema detected (missing Bill). Re-importing..."
fi

echo "Importing $INIT_SQL ..."
"${SQLCMD[@]}" -i "$INIT_SQL"
echo "Importing statistics demo data..."
"${SQLCMD[@]}" -i "$STATISTICS_SEED_SQL"
echo "Import completed."
