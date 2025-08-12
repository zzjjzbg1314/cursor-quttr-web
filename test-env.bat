@echo off
echo === MQTT环境变量测试脚本 ===
echo.

REM 检查关键环境变量
echo 1. 检查关键环境变量:
echo    MQTT_ACCESS_KEY: '%MQTT_ACCESS_KEY%'
echo    MQTT_SECRET_KEY: '%MQTT_SECRET_KEY%'
echo    MQTT_INSTANCE_ID: '%MQTT_INSTANCE_ID%'
echo    MQTT_ENDPOINT: '%MQTT_ENDPOINT%'
echo.

REM 检查环境变量是否为空
echo 2. 检查环境变量是否为空:
if "%MQTT_ACCESS_KEY%"=="" (
    echo    ❌ MQTT_ACCESS_KEY 为空
) else (
    echo    ✅ MQTT_ACCESS_KEY 已设置
)

if "%MQTT_SECRET_KEY%"=="" (
    echo    ❌ MQTT_SECRET_KEY 为空
) else (
    echo    ✅ MQTT_SECRET_KEY 已设置
)

if "%MQTT_INSTANCE_ID%"=="" (
    echo    ❌ MQTT_INSTANCE_ID 为空
) else (
    echo    ✅ MQTT_INSTANCE_ID 已设置
)
echo.

REM 显示所有MQTT相关的环境变量
echo 3. 所有MQTT相关环境变量:
set | findstr /i mqtt
echo.

echo 4. 测试Spring Boot环境变量读取:
echo    启动应用后查看控制台输出的环境变量检查信息
echo.

echo === 测试完成 ===
pause
