# Funções compartilhadas pelos scripts da máquina hospedeira (deploy e experimentos).
# Uso: source deploy/common.sh   (depois de carregar experiments/config.env)

: "${VM_NAME:?defina VM_NAME em experiments/config.env}"
: "${VM_USER:=osboxes}"
: "${SSH_PORT:=2222}"
: "${APP_PORT:=8080}"

SSH_OPTS=(-p "$SSH_PORT" -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile="$HOME/.ssh/known_hosts_ramdex"
          -o ConnectTimeout=5 -o ServerAliveInterval=5 -o LogLevel=ERROR)

log() { printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*"; }
die() { printf 'ERRO: %s\n' "$*" >&2; exit 1; }

vm_ssh() { ssh "${SSH_OPTS[@]}" "$VM_USER@localhost" "$@"; }
vm_ssh_tty() { ssh -t "${SSH_OPTS[@]}" "$VM_USER@localhost" "$@"; }
vm_scp() { scp -P "$SSH_PORT" -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile="$HOME/.ssh/known_hosts_ramdex" -o LogLevel=ERROR "$@"; }

require_vboxmanage() {
    command -v VBoxManage >/dev/null || die "VBoxManage não encontrado (o VirtualBox está instalado nesta máquina?)"
    VBoxManage showvminfo "$VM_NAME" >/dev/null 2>&1 || die "VM '$VM_NAME' não encontrada. Confira VM_NAME em experiments/config.env (VBoxManage list vms)"
}

vm_state() { VBoxManage showvminfo "$VM_NAME" --machinereadable | sed -n 's/^VMState="\(.*\)"/\1/p'; }

# Adiciona os redirecionamentos de porta (NAT): ssh e API. A VM precisa estar desligada.
vm_forward_ports() {
    local info; info="$(VBoxManage showvminfo "$VM_NAME" --machinereadable)"
    grep -q '"ramdex-ssh,' <<<"$info" || VBoxManage modifyvm "$VM_NAME" --natpf1 "ramdex-ssh,tcp,127.0.0.1,$SSH_PORT,,22"
    grep -q '"ramdex-api,' <<<"$info" || VBoxManage modifyvm "$VM_NAME" --natpf1 "ramdex-api,tcp,,$APP_PORT,,8080"
}

vm_start() {
    [[ "$(vm_state)" == "running" ]] && return 0
    VBoxManage startvm "$VM_NAME" --type headless >/dev/null
}

# Desliga a VM com calma (poweroff via ssh, depois botão ACPI) e força só em último caso.
vm_stop() {
    if [[ "$(vm_state)" == "saved" ]]; then
        # VM fechada com "salvar estado": descarta o estado para poder reconfigurar.
        VBoxManage discardstate "$VM_NAME" >/dev/null
        return 0
    fi
    [[ "$(vm_state)" == "running" ]] || return 0
    vm_ssh 'sudo -n /usr/sbin/poweroff' >/dev/null 2>&1 || VBoxManage controlvm "$VM_NAME" acpipowerbutton >/dev/null 2>&1 || true
    for _ in $(seq 90); do
        [[ "$(vm_state)" != "running" ]] && sleep 3 && return 0
        sleep 1
    done
    log "VM não desligou em 90 s; forçando o desligamento"
    VBoxManage controlvm "$VM_NAME" poweroff >/dev/null 2>&1 || true
    sleep 3
}

# Ajusta CPUs, memória (MB) e restrição de execução (%) da VM. A VM precisa estar desligada.
vm_configure() {
    local cpus="$1" mem="$2" cap="${3:-100}"
    # Logo após desligar, a sessão da VM pode continuar travada por alguns segundos.
    for _ in $(seq 15); do
        VBoxManage modifyvm "$VM_NAME" --cpus "$cpus" --memory "$mem" --cpuexecutioncap "$cap" 2>/dev/null && return 0
        sleep 2
    done
    VBoxManage modifyvm "$VM_NAME" --cpus "$cpus" --memory "$mem" --cpuexecutioncap "$cap"
}

wait_ssh() {
    for _ in $(seq 120); do
        vm_ssh true >/dev/null 2>&1 && return 0
        sleep 2
    done
    die "não foi possível conectar por ssh na VM (porta $SSH_PORT)"
}

wait_api() {
    local url="$1"
    for _ in $(seq 90); do
        curl -fs -m 2 "$url/health" >/dev/null 2>&1 && return 0
        sleep 1
    done
    die "a API não respondeu em $url/health"
}
