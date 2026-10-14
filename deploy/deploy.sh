#!/usr/bin/env bash
# Faz o deploy na VM do VirtualBox, a partir da máquina hospedeira.
#
#   ./deploy/deploy.sh            compila (se preciso), copia o .jar para a VM e instala o serviço
#   ./deploy/deploy.sh --ports    só cria os redirecionamentos de porta (VM desligada)
#
# Pré-requisitos: VM criada pelo roteiro, servidor ssh na VM e chave ssh copiada
# (veja experiments/README.md, seção "Preparar a VM").
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT/experiments/config.env"
source "$ROOT/deploy/common.sh"

if [[ "${1:-}" == "--ports" ]]; then
    require_vboxmanage
    [[ "$(vm_state)" == "running" ]] && die "desligue a VM antes de criar os redirecionamentos de porta"
    vm_forward_ports
    log "Redirecionamentos criados: localhost:$SSH_PORT -> VM:22 (ssh) e localhost:$APP_PORT -> VM:8080 (API)"
    exit 0
fi

JAR="$ROOT/target/ramdex-api.jar"
if [[ ! -f "$JAR" ]]; then
    log "Compilando o projeto (mvn package)"
    (cd "$ROOT" && mvn -q -B package -DskipTests)
fi

log "Testando o ssh na VM (localhost:$SSH_PORT)"
vm_ssh true || die "sem acesso ssh. A VM está ligada e a chave foi copiada (ssh-copy-id)?"

log "Copiando arquivos para ~/ramdex na VM"
vm_ssh 'mkdir -p ~/ramdex'
vm_scp "$JAR" "$ROOT/deploy/ramdex.service" "$ROOT/deploy/setup-vm.sh" "$ROOT/experiments/monitor.py" \
    "$VM_USER@localhost:ramdex/"

log "Instalando na VM (o sudo vai pedir a senha: osboxes.org)"
vm_ssh_tty 'bash ~/ramdex/setup-vm.sh'

log "Testando pela máquina hospedeira: http://localhost:$APP_PORT/health"
wait_api "http://localhost:$APP_PORT"
curl -s "http://localhost:$APP_PORT/health"; echo
log "Deploy concluído"
