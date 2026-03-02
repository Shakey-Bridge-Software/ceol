#!/bin/bash
set -e
cd "$(dirname "$0")"

INSTALL_DIR="${INSTALL_DIR:-$HOME/.local/bin}"
BB_DIR="${BB_DIR:-$HOME/.local/share/ceol}"

./bb uberscript ceol.uber.clj -m ceol.core
mkdir -p "$INSTALL_DIR" "$BB_DIR"
cp ./bb "$BB_DIR/bb"
cp ceol.uber.clj "$BB_DIR/ceol.uber.clj"

cat > "$INSTALL_DIR/ceol" << WRAPPER
#!/bin/bash
exec "$BB_DIR/bb" "$BB_DIR/ceol.uber.clj" "\$@"
WRAPPER
chmod +x "$INSTALL_DIR/ceol"

echo "Installed ceol to $INSTALL_DIR/ceol"
