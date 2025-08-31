#!/bin/bash

# 虚拟音频设备设置脚本
# 用于创建音频回环设备以拦截系统音频

echo "========================================"
echo "虚拟音频设备设置工具"
echo "用于同声传译音频拦截"
echo "========================================"

# 检测操作系统
detect_os() {
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        echo "检测到Linux系统"
        return 0
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        echo "检测到macOS系统"
        return 1
    elif [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "cygwin" ]]; then
        echo "检测到Windows系统"
        return 2
    else
        echo "未知操作系统: $OSTYPE"
        return 3
    fi
}

# Linux PulseAudio设置
setup_linux_pulseaudio() {
    echo ""
    echo "设置Linux PulseAudio虚拟音频设备..."
    echo "----------------------------------------"
    
    # 检查PulseAudio是否安装
    if ! command -v pactl &> /dev/null; then
        echo "错误: PulseAudio未安装"
        echo "请运行: sudo apt-get install pulseaudio pulseaudio-utils"
        exit 1
    fi
    
    case "$1" in
        create)
            echo "创建虚拟音频设备..."
            
            # 1. 创建虚拟输出设备（sink）
            echo "1. 创建虚拟扬声器..."
            pactl load-module module-null-sink \
                sink_name=virtual_speaker \
                sink_properties=device.description="Virtual_Speaker_for_Translation"
            
            # 2. 创建对应的监听源（source）
            echo "2. 创建监听源..."
            pactl load-module module-remap-source \
                source_name=virtual_mic \
                master=virtual_speaker.monitor \
                source_properties=device.description="Virtual_Mic_for_Translation"
            
            # 3. 创建组合输出（同时输出到真实扬声器和虚拟设备）
            echo "3. 创建组合输出..."
            pactl load-module module-combine-sink \
                sink_name=combined \
                slaves=virtual_speaker,@DEFAULT_SINK@ \
                sink_properties=device.description="Combined_Output"
            
            # 4. 创建音频回环（可选）
            echo "4. 创建音频回环..."
            pactl load-module module-loopback \
                source=@DEFAULT_SOURCE@ \
                sink=virtual_speaker \
                latency_msec=1
            
            echo ""
            echo "虚拟音频设备创建成功！"
            echo ""
            echo "使用方法:"
            echo "1. 将应用音频输出设置为 'Virtual_Speaker_for_Translation'"
            echo "2. 在录音软件中选择 'Virtual_Mic_for_Translation' 作为输入"
            echo "3. 或使用 'Combined_Output' 同时听到和录制音频"
            ;;
            
        remove)
            echo "移除虚拟音频设备..."
            
            # 获取并卸载相关模块
            pactl list short modules | while read -r line; do
                if echo "$line" | grep -E "module-null-sink|module-remap-source|module-combine-sink|module-loopback" > /dev/null; then
                    module_id=$(echo "$line" | awk '{print $1}')
                    echo "卸载模块 ID: $module_id"
                    pactl unload-module "$module_id"
                fi
            done
            
            echo "虚拟音频设备已移除"
            ;;
            
        list)
            echo "当前音频设备列表:"
            echo ""
            echo "=== 输出设备 (Sinks) ==="
            pactl list short sinks
            echo ""
            echo "=== 输入设备 (Sources) ==="
            pactl list short sources
            echo ""
            echo "=== 加载的模块 ==="
            pactl list short modules | grep -E "module-null-sink|module-remap-source|module-combine-sink|module-loopback"
            ;;
            
        *)
            echo "用法: $0 {create|remove|list}"
            ;;
    esac
}

# Linux ALSA设置
setup_linux_alsa() {
    echo ""
    echo "设置Linux ALSA虚拟音频设备..."
    echo "----------------------------------------"
    
    case "$1" in
        create)
            echo "创建ALSA Loopback设备..."
            
            # 加载snd-aloop模块
            sudo modprobe snd-aloop
            
            # 创建ALSA配置
            cat > ~/.asoundrc << 'EOF'
# ALSA虚拟音频设备配置
# 用于音频拦截和同声传译

# Loopback设备
pcm.loopback {
    type plug
    slave.pcm "hw:Loopback,0,0"
}

pcm.loopback_capture {
    type plug
    slave.pcm "hw:Loopback,1,0"
}

# 软件混音器
pcm.dmixer {
    type dmix
    ipc_key 1024
    slave {
        pcm "hw:0,0"
        period_time 0
        period_size 1024
        buffer_size 4096
        rate 44100
    }
}

# 组合设备（同时输出到扬声器和loopback）
pcm.both {
    type route
    slave.pcm {
        type multi
        slaves.a.pcm "dmixer"
        slaves.b.pcm "loopback"
        slaves.a.channels 2
        slaves.b.channels 2
        bindings.0.slave a
        bindings.0.channel 0
        bindings.1.slave a
        bindings.1.channel 1
        bindings.2.slave b
        bindings.2.channel 0
        bindings.3.slave b
        bindings.3.channel 1
    }
    ttable.0.0 1
    ttable.1.1 1
    ttable.0.2 1
    ttable.1.3 1
}

# 默认设备
pcm.!default {
    type plug
    slave.pcm "both"
}
EOF
            echo "ALSA配置已创建: ~/.asoundrc"
            echo ""
            echo "使用方法:"
            echo "1. 播放音频到 'both' 设备同时输出到扬声器和loopback"
            echo "2. 从 'loopback_capture' 设备录制音频"
            ;;
            
        remove)
            echo "移除ALSA Loopback设备..."
            sudo modprobe -r snd-aloop
            rm -f ~/.asoundrc
            echo "ALSA虚拟设备已移除"
            ;;
            
        list)
            echo "ALSA设备列表:"
            aplay -l
            echo ""
            echo "ALSA PCM设备:"
            aplay -L | grep -E "loopback|both"
            ;;
            
        *)
            echo "用法: $0 {create|remove|list}"
            ;;
    esac
}

# macOS设置说明
setup_macos() {
    echo ""
    echo "macOS虚拟音频设备设置"
    echo "----------------------------------------"
    echo ""
    echo "macOS需要安装第三方虚拟音频设备软件："
    echo ""
    echo "1. BlackHole (推荐，免费开源)"
    echo "   下载: https://existential.audio/blackhole/"
    echo "   安装后会创建 'BlackHole 2ch' 和 'BlackHole 16ch' 设备"
    echo ""
    echo "2. Soundflower (经典选择)"
    echo "   下载: https://github.com/mattingalls/Soundflower"
    echo "   安装后会创建 'Soundflower (2ch)' 和 'Soundflower (64ch)' 设备"
    echo ""
    echo "3. Loopback (付费，功能强大)"
    echo "   官网: https://rogueamoeba.com/loopback/"
    echo ""
    echo "安装后的使用方法:"
    echo "1. 在系统偏好设置 > 声音 中选择虚拟设备作为输出"
    echo "2. 在录音软件中选择相同的虚拟设备作为输入"
    echo "3. 使用 Audio MIDI Setup 创建聚合设备以同时输出到多个设备"
    echo ""
    echo "创建聚合设备:"
    echo "1. 打开 /Applications/Utilities/Audio MIDI Setup.app"
    echo "2. 点击左下角 '+' 选择 'Create Aggregate Device'"
    echo "3. 勾选要组合的设备（如内置输出 + BlackHole）"
    echo "4. 在系统声音设置中选择聚合设备"
}

# Windows设置说明
setup_windows() {
    echo ""
    echo "Windows虚拟音频设备设置"
    echo "----------------------------------------"
    echo ""
    echo "Windows需要安装虚拟音频线缆软件："
    echo ""
    echo "1. VB-Cable (免费)"
    echo "   下载: https://vb-audio.com/Cable/"
    echo "   安装后会创建 'CABLE Input' 和 'CABLE Output' 设备"
    echo ""
    echo "2. Virtual Audio Cable (付费，功能更多)"
    echo "   官网: https://vac.muzychenko.net/"
    echo ""
    echo "3. VoiceMeeter (免费，包含虚拟音频混音器)"
    echo "   下载: https://vb-audio.com/Voicemeeter/"
    echo ""
    echo "安装后的使用方法:"
    echo "1. 在声音设置中将 'CABLE Input' 设为默认播放设备"
    echo "2. 在录音软件中选择 'CABLE Output' 作为录音设备"
    echo "3. 使用 'Listen to this device' 功能同时听到音频"
    echo ""
    echo "启用立体声混音（如果可用）:"
    echo "1. 右键点击音量图标 > 声音"
    echo "2. 录制标签 > 右键 > 显示禁用的设备"
    echo "3. 启用 '立体声混音' 或 'Stereo Mix'"
    echo "4. 设为默认录音设备"
}

# 主菜单
main_menu() {
    echo ""
    echo "请选择操作:"
    echo "1. 创建虚拟音频设备"
    echo "2. 移除虚拟音频设备"
    echo "3. 列出当前音频设备"
    echo "4. 显示使用说明"
    echo "5. 退出"
    echo ""
    read -p "选择 (1-5): " choice
    
    case $choice in
        1) action="create" ;;
        2) action="remove" ;;
        3) action="list" ;;
        4) action="help" ;;
        5) exit 0 ;;
        *) echo "无效选择"; exit 1 ;;
    esac
}

# 主程序
detect_os
os_type=$?

# 如果没有提供参数，显示菜单
if [ $# -eq 0 ]; then
    main_menu
else
    action=$1
fi

case $os_type in
    0)  # Linux
        echo ""
        echo "选择音频系统:"
        echo "1. PulseAudio (推荐)"
        echo "2. ALSA"
        read -p "选择 (1-2): " audio_system
        
        if [ "$audio_system" = "1" ]; then
            setup_linux_pulseaudio "$action"
        else
            setup_linux_alsa "$action"
        fi
        ;;
    1)  # macOS
        setup_macos
        ;;
    2)  # Windows
        setup_windows
        ;;
    *)
        echo "不支持的操作系统"
        exit 1
        ;;
esac

echo ""
echo "完成！"