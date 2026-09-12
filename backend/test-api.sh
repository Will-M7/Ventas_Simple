#!/usr/bin/env bash
set -Eeuo pipefail

BASE_URL="${BASE_URL:-http://localhost:3000}"
TEST_USERNAME="${TEST_USERNAME:-admin}"
TEST_PASSWORD="${TEST_PASSWORD:-admin123}"
TEST_CODE="${TEST_CODE:-QA-$(date +%s)}"
command -v curl >/dev/null || { echo "Falta curl" >&2; exit 1; }
command -v jq >/dev/null || { echo "Falta jq (sudo apt install jq)" >&2; exit 1; }
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
printf '%s' 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=' | base64 -d > "$tmp_dir/test.png"
request() { curl --fail-with-body --silent --show-error --location "$@"; }

echo '1/6 Login'
login_json="$(request -H 'Content-Type: application/json' -d "$(jq -nc --arg u "$TEST_USERNAME" --arg p "$TEST_PASSWORD" '{username:$u,password:$p}')" "$BASE_URL/api/usuarios/login")"
user_id="$(jq -er '.user.id' <<<"$login_json")"
echo '2/6 Crear producto con imagen'
product_json="$(request -X POST -F "codigo_barras=$TEST_CODE" -F 'nombre=Producto QA Linux' -F 'precio_compra=2.50' -F 'precio_venta=5.00' -F 'stock=2' -F "foto=@$tmp_dir/test.png;type=image/png" "$BASE_URL/api/productos")"
product_id="$(jq -er '.id' <<<"$product_json")"
echo '3/6 Buscar por código de barras'
found_json="$(request "$BASE_URL/api/productos/codigo/$TEST_CODE")"
test "$(jq -er '.id' <<<"$found_json")" = "$product_id"
echo '4/6 Incrementar stock'
stock_json="$(request -X PATCH -H 'Content-Type: application/json' -d '{"cantidad":1}' "$BASE_URL/api/productos/$product_id/stock")"
test "$(jq -er '.stock' <<<"$stock_json")" -eq 3
echo '5/6 Registrar venta transaccional'
sale_json="$(request -X POST -H 'Content-Type: application/json' -d "$(jq -nc --argjson uid "$user_id" --argjson pid "$product_id" '{usuario_id:$uid,items:[{producto_id:$pid,cantidad:1}]}')" "$BASE_URL/api/ventas")"
jq -e '.id and .total == 5' <<<"$sale_json" >/dev/null
echo '6/6 Verificar reportes'
request "$BASE_URL/api/reportes/ventas?periodo=dia" | jq -e '.periodo == "dia" and (.cantidad_ventas >= 1)' >/dev/null
request "$BASE_URL/api/reportes/rotacion" | jq -e '.mas_salida and .sin_rotacion_30_dias' >/dev/null
echo "OK: flujo E2E completado (producto $product_id, venta $(jq -r '.id' <<<"$sale_json"))."
