#!/usr/bin/env bash
# Inky MCP Server — SDKMAN setup script
# Installs JBang, GraalVM JDK 21, and optionally Gradle

set -euo pipefail

echo "=== Inky MCP Server Setup ==="
echo ""

# Install SDKMAN if not present
if [ ! -d "$HOME/.sdkman" ]; then
    echo "Installing SDKMAN..."
    curl -s "https://get.sdkman.io" | bash
    source "$HOME/.sdkman/bin/sdkman-init.sh"
else
    source "$HOME/.sdkman/bin/sdkman-init.sh"
    echo "SDKMAN already installed ($(sdk version))"
fi

# Install JDK 21 (Oracle GraalVM)
echo ""
echo "Installing Oracle GraalVM JDK 21..."
sdk install java 21.0.5-graal 2>/dev/null || echo "  Already installed"
sdk use java 21.0.5-graal

# Install JBang
echo ""
echo "Installing JBang..."
sdk install jbang 2>/dev/null || echo "  Already installed"

# Install Gradle (optional, for full builds)
echo ""
echo "Installing Gradle 9..."
sdk install gradle 9.3.1 2>/dev/null || echo "  Already installed"

echo ""
echo "=== Setup Complete ==="
echo ""
echo "Versions:"
java -version 2>&1 | head -1
jbang --version 2>/dev/null || echo "  jbang: (restart shell to activate)"
gradle --version 2>/dev/null | grep "Gradle " || echo "  gradle: (restart shell to activate)"
echo ""
echo "Quick start:"
echo "  cd mcp-server"
echo "  jbang InkyMcp.kt                    # Run with JBang"
echo "  jbang --native InkyMcp.kt           # Build native binary"
echo "  gradle run --args='--port 3001'     # Run with Gradle"
echo ""
echo "Ensure inkjs is installed:"
echo "  cd ../app && npm install"
