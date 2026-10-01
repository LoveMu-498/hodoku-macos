#!/usr/bin/env bash
# Ubuntu/Debian cloud dependency setup; does not package or publish the macOS app.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ "$(uname -s)" != Linux ]]; then
    echo 'Cloud setup targets Linux. On macOS use the existing JDK and check_cloud.py directly.' >&2
    exit 1
fi
if [[ ${1:-} != '' && ${1:-} != --dependencies-only ]]; then
    echo 'Usage: bash script/setup_cloud.sh [--dependencies-only]' >&2
    exit 2
fi
packages=()
install_jdk=0
command -v python3 >/dev/null || packages+=(python3)
command -v git >/dev/null || packages+=(git)
command -v xvfb-run >/dev/null || packages+=(xvfb)
command -v xauth >/dev/null || packages+=(xauth)
# JDK 21 is shared by cloud checks; release packaging keeps its separately pinned macOS JDK.
if ! command -v javac >/dev/null || ! javac -version 2>&1 | grep -Eq '^javac 21([. ]|$)'; then
    packages+=(openjdk-21-jdk)
    install_jdk=1
fi
if command -v dpkg-query >/dev/null; then
    for font in fonts-dejavu-core fonts-noto-cjk; do
        dpkg-query -W -f='${Status}' "$font" 2>/dev/null | grep -q 'install ok installed' || packages+=("$font")
    done
fi
if ((${#packages[@]})); then
    command -v apt-get >/dev/null || { echo 'Install JDK 21, Python 3, Git, Xvfb, xauth and CJK fonts using your cloud image package manager.' >&2; exit 1; }
    elevated=()
    if ((EUID != 0)); then
        command -v sudo >/dev/null || { echo 'Dependency installation requires root or sudo in the cloud container.' >&2; exit 1; }
        elevated=(sudo -n)
    fi
    "${elevated[@]}" apt-get update -qq
    "${elevated[@]}" env DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends "${packages[@]}"
    if ((install_jdk)) && [[ -d /usr/lib/jvm/java-21-openjdk-amd64 ]]; then
        export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
        export PATH="$JAVA_HOME/bin:$PATH"
    elif ((install_jdk)) && [[ -d /usr/lib/jvm/java-21-openjdk-arm64 ]]; then
        export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
        export PATH="$JAVA_HOME/bin:$PATH"
    fi
fi
java -version
javac -version
if [[ ${1:-} != --dependencies-only ]]; then
    xvfb-run -a -s '-screen 0 1600x1200x24' python3 script/check_cloud.py --profile all
    python3 -m unittest discover -s test/packaging -p test_distribution_sources.py
    python3 -m unittest discover -s test/packaging -p test_public_privacy.py
fi
