#!/usr/bin/env python3
"""
同声传译音频拦截系统 - Python实现
演示如何在系统级别拦截音频并进行实时翻译
"""

import pyaudio
import wave
import threading
import queue
import numpy as np
import speech_recognition as sr
from googletrans import Translator
from gtts import gTTS
import pygame
import tempfile
import os
import time
from datetime import datetime

class AudioInterpreter:
    """音频同声传译系统主类"""
    
    def __init__(self):
        # 音频参数
        self.CHUNK = 1024
        self.FORMAT = pyaudio.paInt16
        self.CHANNELS = 1
        self.RATE = 16000
        self.RECORD_SECONDS = 5
        
        # 初始化组件
        self.audio = pyaudio.PyAudio()
        self.recognizer = sr.Recognizer()
        self.translator = Translator()
        pygame.mixer.init()
        
        # 线程控制
        self.is_running = False
        self.audio_queue = queue.Queue()
        self.text_queue = queue.Queue()
        
        # 音频流
        self.stream = None
        self.frames = []
        
    def list_audio_devices(self):
        """列出所有可用的音频设备"""
        print("\n可用的音频输入设备:")
        print("-" * 50)
        for i in range(self.audio.get_device_count()):
            info = self.audio.get_device_info_by_index(i)
            if info['maxInputChannels'] > 0:
                print(f"设备 {i}: {info['name']}")
                print(f"  - 采样率: {int(info['defaultSampleRate'])} Hz")
                print(f"  - 输入通道: {info['maxInputChannels']}")
                print()
        
    def capture_system_audio(self, device_index=None):
        """
        捕获系统音频（需要虚拟音频设备）
        
        Linux: 使用 PulseAudio 的 monitor 设备
        Windows: 需要安装 Virtual Audio Cable 或 VB-Cable
        macOS: 需要安装 Soundflower 或 BlackHole
        """
        try:
            # 尝试找到系统音频回环设备
            for i in range(self.audio.get_device_count()):
                info = self.audio.get_device_info_by_index(i)
                name = info['name'].lower()
                
                # 检查是否是回环设备
                if any(keyword in name for keyword in ['monitor', 'loopback', 'stereo mix', 'virtual', 'cable']):
                    print(f"找到系统音频设备: {info['name']}")
                    device_index = i
                    break
            
            if device_index is None:
                print("未找到系统音频回环设备，使用默认麦克风")
                device_index = self.audio.get_default_input_device_info()['index']
            
            # 打开音频流
            self.stream = self.audio.open(
                format=self.FORMAT,
                channels=self.CHANNELS,
                rate=self.RATE,
                input=True,
                input_device_index=device_index,
                frames_per_buffer=self.CHUNK
            )
            
            print(f"开始捕获音频 (设备: {device_index})")
            return True
            
        except Exception as e:
            print(f"捕获音频失败: {e}")
            return False
    
    def capture_microphone(self):
        """捕获麦克风音频"""
        try:
            self.stream = self.audio.open(
                format=self.FORMAT,
                channels=self.CHANNELS,
                rate=self.RATE,
                input=True,
                frames_per_buffer=self.CHUNK
            )
            print("开始捕获麦克风音频")
            return True
        except Exception as e:
            print(f"捕获麦克风失败: {e}")
            return False
    
    def audio_capture_thread(self):
        """音频捕获线程"""
        while self.is_running:
            try:
                data = self.stream.read(self.CHUNK, exception_on_overflow=False)
                self.frames.append(data)
                
                # 将音频数据放入队列
                self.audio_queue.put(data)
                
                # 每5秒处理一次累积的音频
                if len(self.frames) >= int(self.RATE / self.CHUNK * self.RECORD_SECONDS):
                    # 保存音频数据用于识别
                    audio_data = b''.join(self.frames)
                    self.frames = []
                    
                    # 触发语音识别
                    threading.Thread(target=self.recognize_speech, args=(audio_data,)).start()
                    
            except Exception as e:
                print(f"音频捕获错误: {e}")
                break
    
    def recognize_speech(self, audio_data):
        """语音识别"""
        try:
            # 创建临时WAV文件
            with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp_file:
                wf = wave.open(tmp_file.name, 'wb')
                wf.setnchannels(self.CHANNELS)
                wf.setsampwidth(self.audio.get_sample_size(self.FORMAT))
                wf.setframerate(self.RATE)
                wf.writeframes(audio_data)
                wf.close()
                
                # 使用speech_recognition进行识别
                with sr.AudioFile(tmp_file.name) as source:
                    audio = self.recognizer.record(source)
                    
                    # 尝试识别（这里使用Google的免费API）
                    try:
                        text = self.recognizer.recognize_google(audio, language='zh-CN')
                        print(f"\n[{datetime.now().strftime('%H:%M:%S')}] 识别结果: {text}")
                        
                        # 将文本放入翻译队列
                        self.text_queue.put(text)
                        
                        # 触发翻译
                        threading.Thread(target=self.translate_text, args=(text,)).start()
                        
                    except sr.UnknownValueError:
                        pass  # 无法识别
                    except sr.RequestError as e:
                        print(f"语音识别服务错误: {e}")
                
                # 清理临时文件
                os.unlink(tmp_file.name)
                
        except Exception as e:
            print(f"语音识别错误: {e}")
    
    def translate_text(self, text, target_lang='en'):
        """翻译文本"""
        try:
            # 使用Google Translate进行翻译
            result = self.translator.translate(text, dest=target_lang, src='zh-cn')
            translated = result.text
            
            print(f"[{datetime.now().strftime('%H:%M:%S')}] 翻译结果: {translated}")
            
            # 语音合成
            threading.Thread(target=self.text_to_speech, args=(translated, target_lang)).start()
            
        except Exception as e:
            print(f"翻译错误: {e}")
    
    def text_to_speech(self, text, lang='en'):
        """文本转语音"""
        try:
            # 使用gTTS生成语音
            tts = gTTS(text=text, lang=lang)
            
            # 保存到临时文件
            with tempfile.NamedTemporaryFile(suffix='.mp3', delete=False) as tmp_file:
                tts.save(tmp_file.name)
                
                # 播放语音
                pygame.mixer.music.load(tmp_file.name)
                pygame.mixer.music.play()
                
                # 等待播放完成
                while pygame.mixer.music.get_busy():
                    time.sleep(0.1)
                
                # 清理临时文件
                os.unlink(tmp_file.name)
                
        except Exception as e:
            print(f"语音合成错误: {e}")
    
    def visualize_audio(self):
        """实时音频可视化"""
        while self.is_running:
            try:
                if not self.audio_queue.empty():
                    data = self.audio_queue.get()
                    
                    # 转换为numpy数组
                    audio_array = np.frombuffer(data, dtype=np.int16)
                    
                    # 计算音量级别
                    volume = np.abs(audio_array).mean()
                    
                    # 简单的音量条显示
                    bar_length = int(volume / 100)
                    bar = '█' * min(bar_length, 50)
                    print(f"\r音量: {bar:<50} {volume:.0f}", end='')
                    
            except Exception as e:
                print(f"可视化错误: {e}")
                break
    
    def start(self, source='microphone'):
        """启动同声传译系统"""
        print("\n" + "="*60)
        print("同声传译音频拦截系统 - Python版")
        print("="*60)
        
        # 选择音频源
        if source == 'system':
            if not self.capture_system_audio():
                return
        else:
            if not self.capture_microphone():
                return
        
        self.is_running = True
        
        # 启动音频捕获线程
        capture_thread = threading.Thread(target=self.audio_capture_thread)
        capture_thread.start()
        
        # 启动音频可视化线程
        visualize_thread = threading.Thread(target=self.visualize_audio)
        visualize_thread.start()
        
        print("\n系统已启动，正在监听音频...")
        print("按 Ctrl+C 停止\n")
        
        try:
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            print("\n\n正在停止系统...")
            self.stop()
    
    def stop(self):
        """停止系统"""
        self.is_running = False
        
        if self.stream:
            self.stream.stop_stream()
            self.stream.close()
        
        self.audio.terminate()
        pygame.mixer.quit()
        
        print("系统已停止")


class VirtualAudioDevice:
    """虚拟音频设备管理（Linux PulseAudio示例）"""
    
    @staticmethod
    def create_loopback_linux():
        """在Linux上创建音频回环设备"""
        import subprocess
        
        commands = [
            # 加载module-loopback模块
            "pactl load-module module-loopback latency_msec=1",
            
            # 创建虚拟sink
            "pactl load-module module-null-sink sink_name=virtual_speaker sink_properties=device.description='Virtual_Speaker'",
            
            # 创建监听源
            "pactl load-module module-remap-source source_name=virtual_mic master=virtual_speaker.monitor"
        ]
        
        for cmd in commands:
            try:
                result = subprocess.run(cmd.split(), capture_output=True, text=True)
                print(f"执行: {cmd}")
                print(f"结果: {result.stdout}")
            except Exception as e:
                print(f"命令执行失败: {e}")
    
    @staticmethod
    def remove_loopback_linux():
        """移除Linux上的音频回环设备"""
        import subprocess
        
        try:
            # 列出所有加载的模块
            result = subprocess.run(["pactl", "list", "short", "modules"], capture_output=True, text=True)
            
            # 查找并卸载loopback相关模块
            for line in result.stdout.split('\n'):
                if 'module-loopback' in line or 'module-null-sink' in line or 'module-remap-source' in line:
                    module_id = line.split()[0]
                    subprocess.run(["pactl", "unload-module", module_id])
                    print(f"卸载模块: {module_id}")
                    
        except Exception as e:
            print(f"移除回环设备失败: {e}")


def main():
    """主函数"""
    import argparse
    
    parser = argparse.ArgumentParser(description='同声传译音频拦截系统')
    parser.add_argument('--source', choices=['microphone', 'system'], default='microphone',
                        help='音频源选择')
    parser.add_argument('--list-devices', action='store_true',
                        help='列出所有音频设备')
    parser.add_argument('--create-loopback', action='store_true',
                        help='创建虚拟音频回环设备（仅Linux）')
    parser.add_argument('--remove-loopback', action='store_true',
                        help='移除虚拟音频回环设备（仅Linux）')
    
    args = parser.parse_args()
    
    if args.create_loopback:
        VirtualAudioDevice.create_loopback_linux()
        return
    
    if args.remove_loopback:
        VirtualAudioDevice.remove_loopback_linux()
        return
    
    interpreter = AudioInterpreter()
    
    if args.list_devices:
        interpreter.list_audio_devices()
        return
    
    interpreter.start(source=args.source)


if __name__ == "__main__":
    main()