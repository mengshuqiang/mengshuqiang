<script>
export default {
	onLaunch: function() {
		console.log('App Launch')
		
		// 设置状态栏样式
		// #ifdef APP-PLUS
		plus.navigator.setStatusBarStyle('light')
		plus.navigator.setStatusBarBackground('#7B6FDE')
		// #endif
		
		// 保持屏幕常亮
		// #ifdef APP-PLUS
		plus.device.setWakelock(true)
		// #endif
	},
	onShow: function() {
		console.log('App Show')
	},
	onHide: function() {
		console.log('App Hide')
		
		// 释放屏幕常亮
		// #ifdef APP-PLUS
		plus.device.setWakelock(false)
		// #endif
	}
}
</script>

<style>
/* 全局样式 */
page {
	background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
	height: 100%;
}

/* 全局文本样式 */
text {
	font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

/* 按钮默认样式 */
button {
	border: none;
	outline: none;
}

/* 移除默认的按钮点击效果 */
button::after {
	border: none;
}

/* 滚动条样式 */
::-webkit-scrollbar {
	width: 6px;
	height: 6px;
}

::-webkit-scrollbar-track {
	background: rgba(255, 255, 255, 0.1);
	border-radius: 3px;
}

::-webkit-scrollbar-thumb {
	background: rgba(255, 255, 255, 0.3);
	border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
	background: rgba(255, 255, 255, 0.5);
}

/* 动画效果 */
@keyframes pulse {
	0% {
		transform: scale(1);
		opacity: 1;
	}
	50% {
		transform: scale(1.05);
		opacity: 0.8;
	}
	100% {
		transform: scale(1);
		opacity: 1;
	}
}

@keyframes rotate {
	from {
		transform: rotate(0deg);
	}
	to {
		transform: rotate(360deg);
	}
}

/* 通用类 */
.flex-center {
	display: flex;
	justify-content: center;
	align-items: center;
}

.flex-between {
	display: flex;
	justify-content: space-between;
	align-items: center;
}

.flex-column {
	display: flex;
	flex-direction: column;
}

.text-white {
	color: #ffffff;
}

.text-gray {
	color: #999999;
}

.text-primary {
	color: #7B6FDE;
}

.bg-white {
	background-color: #ffffff;
}

.bg-primary {
	background-color: #7B6FDE;
}

.rounded {
	border-radius: 10px;
}

.shadow {
	box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

.transition {
	transition: all 0.3s ease;
}
</style>