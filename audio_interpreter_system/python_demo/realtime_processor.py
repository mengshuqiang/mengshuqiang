#!/usr/bin/env python3
"""
实时音频处理和播放模块
用于展示低延迟的音频流处理技术
"""

import numpy as np
import sounddevice as sd
import threading
import queue
import time
from collections import deque
import scipy.signal as signal

class RealtimeAudioProcessor:
    """实时音频处理器"""
    
    def __init__(self, sample_rate=44100, channels=2, block_size=512):
        self.sample_rate = sample_rate
        self.channels = channels
        self.block_size = block_size
        
        # 音频缓冲区
        self.input_queue = queue.Queue()
        self.output_queue = queue.Queue()
        
        # 处理状态
        self.is_running = False
        self.processing_thread = None
        
        # 音频流
        self.input_stream = None
        self.output_stream = None
        
        # 延迟缓冲
        self.latency_buffer = deque(maxlen=10)
        
        # 音频效果参数
        self.effects = {
            'volume': 1.0,
            'pitch_shift': 1.0,
            'reverb': 0.0,
            'noise_gate': -40,  # dB
            'compressor': False,
            'eq_enabled': False
        }
        
    def audio_callback_input(self, indata, frames, time_info, status):
        """输入音频回调"""
        if status:
            print(f"输入状态: {status}")
        
        # 将音频数据放入队列
        self.input_queue.put(indata.copy())
        
    def audio_callback_output(self, outdata, frames, time_info, status):
        """输出音频回调"""
        if status:
            print(f"输出状态: {status}")
        
        try:
            # 从输出队列获取处理后的音频
            data = self.output_queue.get_nowait()
            outdata[:] = data
        except queue.Empty:
            # 如果队列为空，输出静音
            outdata.fill(0)
    
    def process_audio(self):
        """音频处理线程"""
        while self.is_running:
            try:
                # 从输入队列获取音频数据
                input_data = self.input_queue.get(timeout=0.1)
                
                # 应用音频处理效果
                processed_data = self.apply_effects(input_data)
                
                # 放入输出队列
                self.output_queue.put(processed_data)
                
                # 计算和显示延迟
                self.calculate_latency()
                
            except queue.Empty:
                continue
            except Exception as e:
                print(f"处理错误: {e}")
    
    def apply_effects(self, audio_data):
        """应用音频效果"""
        data = audio_data.copy()
        
        # 1. 音量调节
        data = data * self.effects['volume']
        
        # 2. 噪声门（Noise Gate）
        if self.effects['noise_gate'] is not None:
            data = self.apply_noise_gate(data, self.effects['noise_gate'])
        
        # 3. 压缩器（Compressor）
        if self.effects['compressor']:
            data = self.apply_compressor(data)
        
        # 4. 均衡器（EQ）
        if self.effects['eq_enabled']:
            data = self.apply_eq(data)
        
        # 5. 混响（Reverb）
        if self.effects['reverb'] > 0:
            data = self.apply_reverb(data, self.effects['reverb'])
        
        # 6. 音高变换（Pitch Shift）
        if self.effects['pitch_shift'] != 1.0:
            data = self.apply_pitch_shift(data, self.effects['pitch_shift'])
        
        # 防止削波
        data = np.clip(data, -1.0, 1.0)
        
        return data
    
    def apply_noise_gate(self, audio, threshold_db):
        """噪声门处理"""
        # 转换阈值到线性
        threshold = 10 ** (threshold_db / 20)
        
        # 计算RMS
        rms = np.sqrt(np.mean(audio ** 2))
        
        # 应用门限
        if rms < threshold:
            # 淡出以避免咔嗒声
            fade_samples = min(64, len(audio))
            fade_out = np.linspace(1, 0, fade_samples)
            audio[:fade_samples] *= fade_out
            audio[fade_samples:] = 0
        
        return audio
    
    def apply_compressor(self, audio, ratio=4, threshold=-20, attack=0.001, release=0.1):
        """动态压缩处理"""
        # 转换参数
        threshold_linear = 10 ** (threshold / 20)
        attack_samples = int(attack * self.sample_rate)
        release_samples = int(release * self.sample_rate)
        
        # 包络跟踪
        envelope = np.abs(audio)
        
        # 平滑包络
        for i in range(1, len(envelope)):
            if envelope[i] > envelope[i-1]:
                # Attack
                envelope[i] = envelope[i-1] + (envelope[i] - envelope[i-1]) / attack_samples
            else:
                # Release
                envelope[i] = envelope[i-1] + (envelope[i] - envelope[i-1]) / release_samples
        
        # 计算增益
        gain = np.ones_like(envelope)
        above_threshold = envelope > threshold_linear
        gain[above_threshold] = (threshold_linear + (envelope[above_threshold] - threshold_linear) / ratio) / envelope[above_threshold]
        
        # 应用增益
        return audio * gain
    
    def apply_eq(self, audio):
        """均衡器处理"""
        # 简单的3段EQ
        # 低频: 100Hz, 中频: 1kHz, 高频: 10kHz
        
        # 低频增强/衰减
        sos_low = signal.butter(2, 100, 'low', fs=self.sample_rate, output='sos')
        low_band = signal.sosfilt(sos_low, audio)
        
        # 中频
        sos_mid = signal.butter(2, [500, 2000], 'band', fs=self.sample_rate, output='sos')
        mid_band = signal.sosfilt(sos_mid, audio)
        
        # 高频
        sos_high = signal.butter(2, 5000, 'high', fs=self.sample_rate, output='sos')
        high_band = signal.sosfilt(sos_high, audio)
        
        # 混合（可调节各频段增益）
        return low_band * 1.2 + mid_band * 1.0 + high_band * 0.8
    
    def apply_reverb(self, audio, amount):
        """简单混响效果"""
        # 使用延迟线实现简单混响
        delays = [0.043, 0.067, 0.087, 0.11]  # 秒
        gains = [0.5, 0.4, 0.3, 0.2]
        
        reverb = np.zeros_like(audio)
        
        for delay, gain in zip(delays, gains):
            delay_samples = int(delay * self.sample_rate)
            if delay_samples < len(audio):
                # 创建延迟信号
                delayed = np.pad(audio, (delay_samples, 0), mode='constant')[:len(audio)]
                reverb += delayed * gain * amount
        
        # 混合原始信号和混响
        return audio * (1 - amount * 0.5) + reverb
    
    def apply_pitch_shift(self, audio, shift_factor):
        """音高变换（简化版）"""
        # 使用重采样实现简单的音高变换
        # 注意：这会改变音频长度，实际应用需要更复杂的算法
        
        if shift_factor == 1.0:
            return audio
        
        # 重采样
        original_length = len(audio)
        resampled_length = int(original_length / shift_factor)
        
        if self.channels == 1:
            resampled = signal.resample(audio, resampled_length)
        else:
            resampled = np.array([signal.resample(audio[:, i], resampled_length) 
                                 for i in range(self.channels)]).T
        
        # 调整长度
        if len(resampled) > original_length:
            return resampled[:original_length]
        else:
            # 填充
            padded = np.zeros_like(audio)
            padded[:len(resampled)] = resampled
            return padded
    
    def calculate_latency(self):
        """计算系统延迟"""
        # 简单的延迟估算
        input_latency = self.input_queue.qsize() * self.block_size / self.sample_rate
        output_latency = self.output_queue.qsize() * self.block_size / self.sample_rate
        total_latency = (input_latency + output_latency) * 1000  # 转换为毫秒
        
        self.latency_buffer.append(total_latency)
        avg_latency = sum(self.latency_buffer) / len(self.latency_buffer)
        
        return avg_latency
    
    def start(self, input_device=None, output_device=None):
        """启动实时音频处理"""
        print("启动实时音频处理器...")
        
        # 创建输入流
        self.input_stream = sd.InputStream(
            device=input_device,
            channels=self.channels,
            samplerate=self.sample_rate,
            blocksize=self.block_size,
            callback=self.audio_callback_input
        )
        
        # 创建输出流
        self.output_stream = sd.OutputStream(
            device=output_device,
            channels=self.channels,
            samplerate=self.sample_rate,
            blocksize=self.block_size,
            callback=self.audio_callback_output
        )
        
        # 启动处理线程
        self.is_running = True
        self.processing_thread = threading.Thread(target=self.process_audio)
        self.processing_thread.start()
        
        # 启动音频流
        self.input_stream.start()
        self.output_stream.start()
        
        print(f"音频处理器已启动")
        print(f"采样率: {self.sample_rate} Hz")
        print(f"块大小: {self.block_size} 样本")
        print(f"理论延迟: {self.block_size / self.sample_rate * 1000:.1f} ms")
    
    def stop(self):
        """停止音频处理"""
        print("停止音频处理器...")
        
        self.is_running = False
        
        if self.processing_thread:
            self.processing_thread.join()
        
        if self.input_stream:
            self.input_stream.stop()
            self.input_stream.close()
        
        if self.output_stream:
            self.output_stream.stop()
            self.output_stream.close()
        
        print("音频处理器已停止")
    
    def set_effect(self, effect_name, value):
        """设置音频效果参数"""
        if effect_name in self.effects:
            self.effects[effect_name] = value
            print(f"设置 {effect_name} = {value}")
        else:
            print(f"未知效果: {effect_name}")
    
    def get_status(self):
        """获取处理器状态"""
        return {
            'running': self.is_running,
            'input_queue_size': self.input_queue.qsize(),
            'output_queue_size': self.output_queue.qsize(),
            'average_latency': sum(self.latency_buffer) / len(self.latency_buffer) if self.latency_buffer else 0,
            'effects': self.effects
        }


class AudioRouter:
    """音频路由器 - 管理多个音频流"""
    
    def __init__(self):
        self.routes = {}
        self.processors = {}
    
    def add_route(self, name, input_device, output_device, processor=None):
        """添加音频路由"""
        route = {
            'input': input_device,
            'output': output_device,
            'processor': processor
        }
        self.routes[name] = route
        print(f"添加路由: {name}")
    
    def start_route(self, name):
        """启动指定路由"""
        if name in self.routes:
            route = self.routes[name]
            processor = RealtimeAudioProcessor()
            processor.start(route['input'], route['output'])
            self.processors[name] = processor
            print(f"路由 {name} 已启动")
        else:
            print(f"路由 {name} 不存在")
    
    def stop_route(self, name):
        """停止指定路由"""
        if name in self.processors:
            self.processors[name].stop()
            del self.processors[name]
            print(f"路由 {name} 已停止")
    
    def list_routes(self):
        """列出所有路由"""
        for name, route in self.routes.items():
            status = "运行中" if name in self.processors else "已停止"
            print(f"{name}: {route['input']} -> {route['output']} [{status}]")


def demo_realtime_processing():
    """演示实时音频处理"""
    print("\n" + "="*60)
    print("实时音频处理演示")
    print("="*60)
    
    # 列出可用设备
    print("\n可用音频设备:")
    print(sd.query_devices())
    
    # 创建处理器
    processor = RealtimeAudioProcessor(
        sample_rate=44100,
        channels=2,
        block_size=256  # 降低延迟
    )
    
    # 启动处理
    processor.start()
    
    print("\n控制命令:")
    print("v <value>  - 设置音量 (0.0-2.0)")
    print("p <value>  - 设置音高 (0.5-2.0)")
    print("r <value>  - 设置混响 (0.0-1.0)")
    print("c          - 切换压缩器")
    print("e          - 切换均衡器")
    print("s          - 显示状态")
    print("q          - 退出")
    print()
    
    try:
        while True:
            cmd = input("> ").strip().split()
            
            if not cmd:
                continue
            
            if cmd[0] == 'q':
                break
            elif cmd[0] == 'v' and len(cmd) > 1:
                processor.set_effect('volume', float(cmd[1]))
            elif cmd[0] == 'p' and len(cmd) > 1:
                processor.set_effect('pitch_shift', float(cmd[1]))
            elif cmd[0] == 'r' and len(cmd) > 1:
                processor.set_effect('reverb', float(cmd[1]))
            elif cmd[0] == 'c':
                current = processor.effects['compressor']
                processor.set_effect('compressor', not current)
            elif cmd[0] == 'e':
                current = processor.effects['eq_enabled']
                processor.set_effect('eq_enabled', not current)
            elif cmd[0] == 's':
                status = processor.get_status()
                print(f"状态: {status}")
            else:
                print("未知命令")
                
    except KeyboardInterrupt:
        pass
    finally:
        processor.stop()


if __name__ == "__main__":
    demo_realtime_processing()