#!/bin/bash
# cMouse macOS 接收端一键安装脚本
# 用法：curl -fsSL https://raw.githubusercontent.com/cgbin24/cMouse/main/scripts/install-macos.sh | bash
# 原理：curl 下载不带 Gatekeeper 隔离属性，绕开"无法验证开发者"拦截；
#       下载后自动做 SHA-256 校验，再由系统 installer 写入 /Applications。
set -euo pipefail

REPO="cgbin24/cMouse"
BASE="https://github.com/$REPO/releases/latest/download"
DL=$(mktemp -d)
trap 'rm -rf "$DL"' EXIT

echo "==> 下载 cMouse 接收端（最新 Release）..."
curl -fsSL "$BASE/cMouse-Receiver-macOS.pkg" -o "$DL/cMouse.pkg"

if curl -fsSL "$BASE/SHA256SUMS-macos.txt" -o "$DL/SHA256SUMS-macos.txt" 2>/dev/null; then
  EXPECT=$(grep 'cMouse-Receiver-macOS.pkg' "$DL/SHA256SUMS-macos.txt" | awk '{print $1}')
  ACTUAL=$(shasum -a 256 "$DL/cMouse.pkg" | awk '{print $1}')
  if [ -n "$EXPECT" ] && [ "$EXPECT" != "$ACTUAL" ]; then
    echo "!! SHA-256 校验失败：下载内容与官方不一致，已中止。"
    exit 1
  fi
  echo "==> SHA-256 校验通过"
else
  echo "!! 未获取到校验文件，跳过校验（不影响安装）"
fi

echo "==> 安装到 /Applications（需要输入管理员密码）..."
sudo installer -pkg "$DL/cMouse.pkg" -target /

echo ""
echo "==> 完成！打开方式：应用程序 → cMouse（菜单栏出现 ⌖ 图标）"
echo "==> 首次使用：系统设置 → 隐私与安全性 → 辅助功能 → 勾选 cMouse"
echo "==> 卸载：退出 cMouse 后把 /Applications/cMouse.app 拖入废纸篓，"
echo "    再删除 ~/Library/Application Support/cMouse/ 即完全清除。"
