// ==UserScript==
// @name         Annict Episode Checker
// @namespace    https://github.com/Ibemu
// @version      0.2
// @description  個人ページや放送予定ページでマウスオーバーした作品のエピソード一覧をポップアップします
// @author       Ibemu
// @match        https://annict.jp/@*
// @match        https://annict.jp/programs
// @grant        GM_xmlhttpRequest
// @grant        GM_addStyle
// ==/UserScript==

(function() {
    'use strict';
    
    // 添加弹窗样式
    GM_addStyle(`
        .episode-popup {
            position: absolute;
            background: white;
            border: 1px solid #ddd;
            border-radius: 8px;
            padding: 12px;
            box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
            z-index: 10000;
            max-width: 400px;
            max-height: 300px;
            overflow-y: auto;
            display: none;
        }
        .episode-popup.show {
            display: block;
        }
        .episode-popup h4 {
            margin: 0 0 8px 0;
            font-size: 14px;
            font-weight: bold;
            color: #333;
            border-bottom: 1px solid #eee;
            padding-bottom: 4px;
        }
        .episode-list {
            list-style: none;
            margin: 0;
            padding: 0;
        }
        .episode-item {
            padding: 4px 0;
            font-size: 12px;
            color: #666;
            border-bottom: 1px solid #f0f0f0;
        }
        .episode-item:last-child {
            border-bottom: none;
        }
        .episode-number {
            font-weight: bold;
            color: #333;
        }
        .episode-title {
            margin-left: 8px;
        }
        .loading-text {
            color: #999;
            font-style: italic;
        }
    `);
    
    // 创建弹窗元素
    const popup = document.createElement('div');
    popup.className = 'episode-popup';
    document.body.appendChild(popup);
    
    let currentTimer = null;
    let hideTimer = null;
    
    // 从作品链接中提取作品ID
    function extractWorkId(element) {
        const link = element.querySelector('a[href*="/works/"]') || element.closest('a[href*="/works/"]');
        if (link) {
            const match = link.href.match(/\/works\/(\d+)/);
            return match ? match[1] : null;
        }
        return null;
    }
    
    // 获取剧集列表（使用 Annict 页面数据）
    async function fetchEpisodes(workId) {
        return new Promise((resolve, reject) => {
            GM_xmlhttpRequest({
                method: 'GET',
                url: `https://annict.jp/works/${workId}/episodes`,
                onload: function(response) {
                    if (response.status === 200) {
                        const parser = new DOMParser();
                        const doc = parser.parseFromString(response.responseText, 'text/html');
                        
                        // 提取剧集信息
                        const episodes = [];
                        const episodeElements = doc.querySelectorAll('.c-episode-list-item');
                        
                        episodeElements.forEach(elem => {
                            const numberElem = elem.querySelector('.c-episode-list-item__number');
                            const titleElem = elem.querySelector('.c-episode-list-item__title');
                            
                            if (numberElem) {
                                episodes.push({
                                    number: numberElem.textContent.trim(),
                                    title: titleElem ? titleElem.textContent.trim() : ''
                                });
                            }
                        });
                        
                        // 如果没有找到新版本的元素，尝试旧版本
                        if (episodes.length === 0) {
                            const oldEpisodeElements = doc.querySelectorAll('.episode-item, .episodes-list-item');
                            oldEpisodeElements.forEach(elem => {
                                const text = elem.textContent.trim();
                                if (text) {
                                    episodes.push({
                                        number: episodes.length + 1,
                                        title: text
                                    });
                                }
                            });
                        }
                        
                        resolve(episodes);
                    } else {
                        reject(new Error('Failed to fetch episodes'));
                    }
                },
                onerror: function() {
                    reject(new Error('Network error'));
                }
            });
        });
    }
    
    // 显示弹窗
    function showPopup(element, workId) {
        const rect = element.getBoundingClientRect();
        
        // 设置弹窗位置
        popup.style.left = rect.left + 'px';
        popup.style.top = (rect.bottom + window.scrollY + 5) + 'px';
        
        // 显示加载中
        popup.innerHTML = '<div class="loading-text">エピソード読み込み中...</div>';
        popup.classList.add('show');
        
        // 获取剧集列表
        fetchEpisodes(workId)
            .then(episodes => {
                if (episodes.length > 0) {
                    let html = '<h4>エピソード一覧</h4><ul class="episode-list">';
                    episodes.forEach(ep => {
                        html += `
                            <li class="episode-item">
                                <span class="episode-number">${ep.number}</span>
                                ${ep.title ? `<span class="episode-title">${ep.title}</span>` : ''}
                            </li>
                        `;
                    });
                    html += '</ul>';
                    popup.innerHTML = html;
                } else {
                    popup.innerHTML = '<div class="loading-text">エピソード情報が見つかりません</div>';
                }
            })
            .catch(error => {
                console.error('Error fetching episodes:', error);
                popup.innerHTML = '<div class="loading-text">エピソード取得エラー</div>';
            });
    }
    
    // 隐藏弹窗
    function hidePopup() {
        popup.classList.remove('show');
    }
    
    // 设置事件监听
    function setupListeners() {
        // 查找所有作品卡片或链接
        const workElements = document.querySelectorAll(
            '.work-item, .program-item, ' +
            'a[href*="/works/"], ' +
            '.c-work-card, .work-card, ' +
            '.c-program-list-item, .program-list-item'
        );
        
        workElements.forEach(element => {
            // 避免重复绑定
            if (element.dataset.episodeListenerAdded) return;
            element.dataset.episodeListenerAdded = 'true';
            
            element.addEventListener('mouseenter', function() {
                // 清除隐藏定时器
                if (hideTimer) {
                    clearTimeout(hideTimer);
                    hideTimer = null;
                }
                
                // 设置显示定时器（延迟300ms显示，避免误触）
                currentTimer = setTimeout(() => {
                    const workId = extractWorkId(element);
                    if (workId) {
                        showPopup(element, workId);
                    }
                }, 300);
            });
            
            element.addEventListener('mouseleave', function() {
                // 清除显示定时器
                if (currentTimer) {
                    clearTimeout(currentTimer);
                    currentTimer = null;
                }
                
                // 设置隐藏定时器（延迟200ms隐藏，给用户时间移动到弹窗）
                hideTimer = setTimeout(() => {
                    hidePopup();
                }, 200);
            });
        });
        
        // 弹窗本身的鼠标事件
        popup.addEventListener('mouseenter', function() {
            if (hideTimer) {
                clearTimeout(hideTimer);
                hideTimer = null;
            }
        });
        
        popup.addEventListener('mouseleave', function() {
            hideTimer = setTimeout(() => {
                hidePopup();
            }, 200);
        });
    }
    
    // 初始化
    function init() {
        // 等待页面加载完成
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', setupListeners);
        } else {
            setupListeners();
        }
        
        // 监听动态内容加载（SPA页面）
        const observer = new MutationObserver(function(mutations) {
            setupListeners();
        });
        
        observer.observe(document.body, {
            childList: true,
            subtree: true
        });
    }
    
    init();
    console.log('Annict Episode Checker loaded successfully');
})();