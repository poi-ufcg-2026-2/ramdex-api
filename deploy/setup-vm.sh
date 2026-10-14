#!/usr/bin/env bash
# Prepara a VM (Ubuntu Server 22.04 do roteiro) para rodar a RAMdex API.
# Executado DENTRO da VM pelo deploy.sh (pede a senha do sudo uma vez: osboxes.org).
#
#  - instala o Java 17 (JRE headless) e o curl
#  - instala o serviço systemd "ramdex" (sobe sozinho quando a VM liga)
#  - libera, sem senha, apenas os comandos que o runner dos experimentos precisa
#    (reiniciar o serviço e desligar a VM)
set -euo pipefail

APP_DIR="$HOME/ramdex"
USER_NAME="$(id -un)"

echo "==> Instalando Java 17 e utilitários"
sudo apt-get update -qq
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-17-jre-headless curl python3 >/dev/null
java -version 2>&1 | head -1

echo "==> Configurando o serviço systemd"
if [[ ! -f "$APP_DIR/ramdex.env" ]]; then
    cat > "$APP_DIR/ramdex.env" <<'EOF'
# Heap = 70% da RAM da VM: ao mudar a memória no VirtualBox, o heap acompanha sozinho.
JAVA_OPTS=-XX:MaxRAMPercentage=70
PORT=8080
EOF
fi
sed "s#/home/osboxes#$HOME#g; s#^User=.*#User=$USER_NAME#" "$APP_DIR/ramdex.service" \
    | sudo tee /etc/systemd/system/ramdex.service >/dev/null
sudo systemctl daemon-reload
sudo systemctl enable ramdex >/dev/null 2>&1
sudo systemctl restart ramdex

echo "==> Liberando comandos do runner sem senha"
SUDOERS_TMP="$(mktemp)"
cat > "$SUDOERS_TMP" <<EOF
$USER_NAME ALL=(root) NOPASSWD: /usr/bin/systemctl restart ramdex, /usr/bin/systemctl stop ramdex, /usr/bin/systemctl start ramdex, /usr/sbin/poweroff
EOF
sudo visudo -cf "$SUDOERS_TMP" >/dev/null
sudo install -m 0440 "$SUDOERS_TMP" /etc/sudoers.d/ramdex
rm -f "$SUDOERS_TMP"

echo "==> Aguardando a API responder"
for _ in $(seq 60); do
    if curl -fs "http://localhost:8080/health" >/dev/null 2>&1; then
        curl -s "http://localhost:8080/health"; echo
        echo "==> Pronto: RAMdex rodando como serviço (sudo systemctl status ramdex)"
        exit 0
    fi
    sleep 1
done
echo "A API não respondeu em 60 s. Veja: sudo journalctl -u ramdex -n 50" >&2
exit 1
