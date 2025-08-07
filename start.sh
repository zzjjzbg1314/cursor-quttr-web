#!/bin/bash

# Cursor Quitter Web 启动脚本

echo "=== Cursor Quitter Web 启动脚本 ==="

# 检查Java版本
echo "检查Java版本..."
java -version
if [ $? -ne 0 ]; then
    echo "错误: 未找到Java，请先安装Java 8或更高版本"
    exit 1
fi

# 检查Maven
echo "检查Maven..."
if ! command -v mvn &> /dev/null; then
    echo "警告: 未找到Maven，尝试使用Maven Wrapper..."
    if [ -f "./mvnw" ]; then
        echo "使用Maven Wrapper..."
        MVN_CMD="./mvnw"
    else
        echo "错误: 未找到Maven或Maven Wrapper，请先安装Maven"
        echo "安装命令: brew install maven"
        exit 1
    fi
else
    MVN_CMD="mvn"
fi

# 检查配置文件
echo "检查配置文件..."
if [ ! -f "src/main/resources/application.yml" ]; then
    echo "错误: 未找到配置文件 application.yml"
    exit 1
fi

# 检查SSL证书
echo "检查SSL证书..."
if [ -f "src/main/resources/kejiapi.cn.jks" ]; then
    echo "✓ 找到SSL证书文件: kejiapi.cn.jks"
    
    # 检查SSL环境变量
    if [ -z "$SSL_KEY_STORE_PASSWORD" ]; then
        echo "警告: 未设置SSL_KEY_STORE_PASSWORD环境变量"
        echo "请设置SSL证书密码:"
        echo "export SSL_KEY_STORE_PASSWORD=your_keystore_password"
        echo ""
    else
        echo "✓ SSL证书密码已配置"
    fi
    
    if [ -z "$SSL_KEY_ALIAS" ]; then
        echo "警告: 未设置SSL_KEY_ALIAS环境变量，将使用默认别名: kejiapi.cn"
        echo "请设置SSL证书别名:"
        echo "export SSL_KEY_ALIAS=kejiapi.cn"
        echo ""
    else
        echo "✓ SSL证书别名已配置: $SSL_KEY_ALIAS"
    fi
else
    echo "警告: 未找到SSL证书文件，将使用HTTP模式"
fi

# 检查微信配置
echo "检查微信配置..."
if [ -z "$WECHAT_APP_ID" ] && [ -z "$WECHAT_APP_SECRET" ]; then
    echo "警告: 未设置微信环境变量，请确保在application.yml中配置了微信AppID和AppSecret"
    echo "或者设置环境变量:"
    echo "export WECHAT_APP_ID=your_wechat_app_id"
    echo "export WECHAT_APP_SECRET=your_wechat_app_secret"
fi

# 编译项目
echo "编译项目..."
$MVN_CMD clean compile
if [ $? -ne 0 ]; then
    echo "错误: 项目编译失败"
    exit 1
fi

# 运行测试
echo "运行测试..."
$MVN_CMD test
if [ $? -ne 0 ]; then
    echo "警告: 测试失败，但继续启动应用..."
fi

# 启动应用
echo "启动应用..."
if [ -f "src/main/resources/kejiapi.cn.jks" ] && [ ! -z "$SSL_KEY_STORE_PASSWORD" ]; then
    echo "应用将在 https://localhost:8080 启动（HTTPS模式）"
    echo "微信登录接口: https://localhost:8080/api/wechat/login"
else
    echo "应用将在 http://localhost:8080 启动（HTTP模式）"
    echo "微信登录接口: http://localhost:8080/api/wechat/login"
fi
echo "按 Ctrl+C 停止应用"
echo ""

$MVN_CMD spring-boot:run 