/**
 * Real-time Call Player with Audio + Transcription
 * 
 * Flow:
 * 1. User clicks play -> Audio starts
 * 2. During playback: "Listening to call..." with waveform
 * 3. Audio ends -> "Processing speech-to-text with Whisper..."
 * 4. After processing delay -> Full transcription appears
 * 5. Process button enabled
 */

class CallPlayer {
    constructor() {
        this.audio = null;
        this.currentScenario = null;
        this.isProcessing = false;
    }

    async playCall(scenario) {
        this.currentScenario = scenario;
        this.isProcessing = false;

        const container = document.getElementById('call-results-container');

        // Show audio player UI
        container.innerHTML = `
            <div class="space-y-6">
                <!-- Audio Player -->
                <div class="bg-gradient-to-r from-purple-50 to-blue-50 rounded-xl p-6">
                    <div class="flex items-center justify-between mb-4">
                        <div>
                            <h3 class="text-lg font-bold text-gray-800">📞 ${scenario.name}</h3>
                            <p class="text-sm text-gray-600">${scenario.description}</p>
                        </div>
                        <div class="text-3xl">🎙️</div>
                    </div>

                    <!-- Audio Controls -->
                    <div class="bg-white rounded-lg p-4 shadow-sm">
                        <audio id="call-audio" src="${scenario.audio_url}" class="hidden"></audio>

                        <div class="flex items-center gap-4">
                            <button
                                id="play-btn"
                                onclick="window.callPlayer.togglePlay()"
                                class="w-14 h-14 rounded-full bg-gradient-to-r from-purple-500 to-blue-500 text-white flex items-center justify-center hover:shadow-lg transition-all duration-200 text-xl"
                            >
                                <span id="play-icon">▶️</span>
                            </button>

                            <div class="flex-1">
                                <div class="flex justify-between text-xs text-gray-600 mb-1">
                                    <span id="current-time">0:00</span>
                                    <span id="duration">0:00</span>
                                </div>
                                <div class="w-full bg-gray-200 rounded-full h-2 cursor-pointer" onclick="window.callPlayer.seek(event)">
                                    <div id="progress-bar" class="bg-gradient-to-r from-purple-500 to-blue-500 h-2 rounded-full transition-all duration-100" style="width: 0%"></div>
                                </div>
                            </div>
                        </div>

                        <!-- Waveform Animation (while playing) -->
                        <div id="waveform" class="hidden mt-4 flex justify-center gap-1 h-12 items-end">
                            ${Array(30).fill().map((_, i) => `
                                <div class="w-1 bg-purple-500 rounded-full transition-all duration-75" 
                                     style="height: 20%; animation: waveform ${0.3 + Math.random() * 0.4}s ease-in-out infinite; animation-delay: ${i * 0.03}s"></div>
                            `).join('')}
                        </div>
                    </div>
                </div>

                <!-- Transcription Box - Initially shows waiting state -->
                <div id="transcription-box" class="bg-white rounded-xl p-6 card-shadow">
                    <div id="transcription-waiting" class="text-center py-8">
                        <div class="text-5xl mb-4">🎧</div>
                        <p class="text-lg font-semibold text-gray-700">Click play to start listening</p>
                        <p class="text-sm text-gray-500 mt-2">Audio will be transcribed after playback</p>
                    </div>
                    
                    <div id="transcription-listening" class="hidden">
                        <div class="flex items-center gap-3 mb-4">
                            <div class="flex gap-1">
                                <div class="w-2 h-2 bg-red-500 rounded-full animate-pulse"></div>
                                <div class="w-2 h-2 bg-red-500 rounded-full animate-pulse" style="animation-delay: 0.2s"></div>
                                <div class="w-2 h-2 bg-red-500 rounded-full animate-pulse" style="animation-delay: 0.4s"></div>
                            </div>
                            <span class="text-red-600 font-semibold">LIVE - Listening to call...</span>
                        </div>
                        <div class="bg-gray-50 rounded-lg p-4 min-h-[80px] flex items-center justify-center">
                            <div class="text-gray-400 text-sm">
                                <span class="inline-block animate-pulse">Capturing audio stream...</span>
                            </div>
                        </div>
                    </div>
                    
                    <div id="transcription-processing" class="hidden text-center py-8">
                        <div class="loading mx-auto mb-4"></div>
                        <p class="text-lg font-semibold text-gray-700">Processing with Whisper...</p>
                        <p class="text-sm text-gray-500 mt-2">Converting speech to text</p>
                    </div>
                    
                    <div id="transcription-result" class="hidden">
                        <div class="flex items-center gap-2 mb-4">
                            <span class="text-green-600 text-xl">✓</span>
                            <span class="text-green-600 font-semibold">TRANSCRIPTION COMPLETE</span>
                            <span class="text-sm text-gray-500">(${scenario.transcription.confidence * 100}% confidence)</span>
                        </div>
                        <div class="bg-gray-50 rounded-lg p-4">
                            <p id="transcription-text" class="text-lg text-gray-800 leading-relaxed"></p>
                        </div>
                        <div class="mt-3 flex flex-wrap gap-2 text-xs">
                            <span class="bg-blue-100 text-blue-700 px-2 py-1 rounded">Duration: ${scenario.transcription.duration}s</span>
                            <span class="bg-purple-100 text-purple-700 px-2 py-1 rounded">Speaking Rate: ${scenario.transcription.metadata.speaking_rate} wpm</span>
                            <span class="bg-green-100 text-green-700 px-2 py-1 rounded">Language: Turkish</span>
                        </div>
                    </div>
                </div>

                <!-- Process Button -->
                <button
                    id="process-btn"
                    onclick="window.callPlayer.processCall()"
                    disabled
                    class="w-full py-4 rounded-lg bg-gray-300 text-gray-500 font-semibold cursor-not-allowed transition-all duration-200"
                >
                    🎧 Listen to audio first
                </button>

                <!-- Processing Results -->
                <div id="processing-results"></div>
            </div>
            
            <style>
                @keyframes waveform {
                    0%, 100% { height: 20%; }
                    50% { height: ${40 + Math.random() * 60}%; }
                }
            </style>
        `;

        // Initialize audio
        this.audio = document.getElementById('call-audio');

        // Audio event listeners
        this.audio.addEventListener('loadedmetadata', () => {
            document.getElementById('duration').textContent = this.formatTime(this.audio.duration);
        });

        this.audio.addEventListener('timeupdate', () => {
            this.updateProgress();
        });

        this.audio.addEventListener('ended', () => {
            this.onAudioEnded();
        });

        this.audio.addEventListener('play', () => {
            this.onAudioPlay();
        });

        this.audio.addEventListener('pause', () => {
            this.onAudioPause();
        });
    }

    togglePlay() {
        if (this.isProcessing) return;
        
        if (this.audio.paused) {
            this.audio.play();
        } else {
            this.audio.pause();
        }
    }

    onAudioPlay() {
        document.getElementById('play-icon').textContent = '⏸️';
        document.getElementById('waveform').classList.remove('hidden');
        
        // Show listening state
        document.getElementById('transcription-waiting').classList.add('hidden');
        document.getElementById('transcription-listening').classList.remove('hidden');
        document.getElementById('transcription-processing').classList.add('hidden');
        document.getElementById('transcription-result').classList.add('hidden');
    }

    onAudioPause() {
        if (!this.isProcessing) {
            document.getElementById('play-icon').textContent = '▶️';
            document.getElementById('waveform').classList.add('hidden');
        }
    }

    seek(event) {
        const progressBar = event.currentTarget;
        const rect = progressBar.getBoundingClientRect();
        const percent = (event.clientX - rect.left) / rect.width;
        this.audio.currentTime = percent * this.audio.duration;
    }

    updateProgress() {
        const percent = (this.audio.currentTime / this.audio.duration) * 100;
        document.getElementById('progress-bar').style.width = `${percent}%`;
        document.getElementById('current-time').textContent = this.formatTime(this.audio.currentTime);
    }

    formatTime(seconds) {
        const mins = Math.floor(seconds / 60);
        const secs = Math.floor(seconds % 60);
        return `${mins}:${secs.toString().padStart(2, '0')}`;
    }

    async onAudioEnded() {
        this.isProcessing = true;
        
        document.getElementById('play-icon').textContent = '✓';
        document.getElementById('waveform').classList.add('hidden');

        // Show processing state
        document.getElementById('transcription-listening').classList.add('hidden');
        document.getElementById('transcription-processing').classList.remove('hidden');

        // Simulate Whisper processing time (proportional to audio length)
        const processingTime = Math.min(this.currentScenario.transcription.duration * 150, 3000);
        
        await new Promise(resolve => setTimeout(resolve, processingTime));

        // Show transcription result with typing effect
        document.getElementById('transcription-processing').classList.add('hidden');
        document.getElementById('transcription-result').classList.remove('hidden');
        
        await this.typeTranscription(this.currentScenario.transcription.text);

        this.isProcessing = false;

        // Enable process button
        const processBtn = document.getElementById('process-btn');
        processBtn.disabled = false;
        processBtn.classList.remove('bg-gray-300', 'text-gray-500', 'cursor-not-allowed');
        processBtn.classList.add('bg-gradient-to-r', 'from-green-500', 'to-blue-500', 'text-white', 'hover:shadow-lg', 'cursor-pointer');
        processBtn.textContent = '🚀 Analyze Call with AI Agent';
    }

    async typeTranscription(text) {
        const el = document.getElementById('transcription-text');
        el.textContent = '';
        
        const words = text.split(' ');
        
        for (let i = 0; i < words.length; i++) {
            el.textContent += words[i] + ' ';
            // Typing speed: faster for longer texts
            const delay = Math.max(30, 80 - (words.length * 1.5));
            await new Promise(resolve => setTimeout(resolve, delay));
        }
    }

    async processCall() {
        const resultsContainer = document.getElementById('processing-results');

        resultsContainer.innerHTML = `
            <div class="bg-white rounded-xl p-6 card-shadow">
                <div class="text-center py-12">
                    <div class="loading mx-auto mb-4"></div>
                    <p class="text-gray-600 font-semibold">Analyzing call with AI Agent...</p>
                    <p class="text-sm text-gray-500 mt-2">Sentiment Analysis → Intent Detection → Routing</p>
                </div>
            </div>
        `;

        try {
            const response = await fetch('/api/call/process', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    scenario_id: this.currentScenario.id,
                    customer_id: this.currentScenario.customer_id
                })
            });

            const result = await response.json();

            // Update tests counter
            const testsRun = parseInt(document.getElementById('tests-run').textContent) + 1;
            document.getElementById('tests-run').textContent = testsRun;

            const priorityColors = {
                'URGENT': 'badge-urgent',
                'HIGH': 'badge-high',
                'MEDIUM': 'badge-medium',
                'LOW': 'badge-low'
            };

            resultsContainer.innerHTML = `
                <div class="bg-white rounded-xl p-6 card-shadow space-y-4">
                    <div class="flex items-center justify-between pb-4 border-b">
                        <h3 class="text-lg font-bold">📊 Analysis Results</h3>
                        <span class="text-sm text-gray-500">Case ID: ${result.case_id}</span>
                    </div>

                    <!-- Speech Metadata -->
                    <div class="bg-blue-50 rounded-lg p-4">
                        <p class="text-sm font-semibold text-blue-600 mb-3">🎙️ SPEECH METADATA</p>
                        <div class="grid grid-cols-2 gap-2 text-xs">
                            <div class="flex justify-between">
                                <span class="text-gray-600">Confidence:</span>
                                <span class="font-semibold">${(result.call_metadata.transcription_confidence * 100).toFixed(0)}%</span>
                            </div>
                            <div class="flex justify-between">
                                <span class="text-gray-600">Duration:</span>
                                <span class="font-semibold">${result.call_metadata.call_duration}s</span>
                            </div>
                            <div class="flex justify-between">
                                <span class="text-gray-600">Speaking Rate:</span>
                                <span class="font-semibold">${result.call_metadata.speaking_rate} wpm</span>
                            </div>
                            <div class="flex justify-between">
                                <span class="text-gray-600">Pitch Variance:</span>
                                <span class="font-semibold">${result.call_metadata.pitch_variance}</span>
                            </div>
                        </div>
                        ${result.call_metadata.emotion_markers && result.call_metadata.emotion_markers.length > 0 ? `
                            <div class="mt-3 pt-3 border-t border-blue-200">
                                <p class="text-xs text-blue-600 font-semibold mb-2">Emotion Markers:</p>
                                <div class="flex flex-wrap gap-2">
                                    ${result.call_metadata.emotion_markers.map(marker => `
                                        <span class="bg-blue-100 text-blue-800 px-2 py-1 rounded text-xs">${marker}</span>
                                    `).join('')}
                                </div>
                            </div>
                        ` : ''}
                    </div>

                    <div class="grid grid-cols-2 gap-4">
                        <div class="bg-purple-50 rounded-lg p-4">
                            <p class="text-xs text-purple-600 font-semibold mb-2">CATEGORY</p>
                            <p class="text-lg font-bold">${result.category}</p>
                        </div>
                        <div class="bg-purple-50 rounded-lg p-4">
                            <p class="text-xs text-purple-600 font-semibold mb-2">PRIORITY</p>
                            <span class="${priorityColors[result.priority]} text-white px-3 py-1 rounded-full text-sm font-bold">
                                ${result.priority}
                            </span>
                        </div>
                    </div>

                    <div class="bg-blue-50 rounded-lg p-4">
                        <p class="text-xs text-blue-600 font-semibold mb-2">🎯 INTENT</p>
                        <p class="text-gray-800">${result.intent}</p>
                    </div>

                    <div class="bg-green-50 rounded-lg p-4">
                        <p class="text-xs text-green-600 font-semibold mb-3">⚡ ACTIONS</p>
                        <div class="space-y-2">
                            ${result.actions_taken.map(action => `
                                <div class="flex items-start gap-2">
                                    <span class="text-green-600 mt-1">✓</span>
                                    <span class="text-sm text-gray-700">${action}</span>
                                </div>
                            `).join('')}
                        </div>
                    </div>

                    <div class="bg-gray-50 rounded-lg p-4">
                        <p class="text-xs text-gray-600 font-semibold mb-2">🧠 AI REASONING</p>
                        <p class="text-sm text-gray-700 leading-relaxed">${result.reasoning}</p>
                    </div>
                </div>
            `;

        } catch (error) {
            resultsContainer.innerHTML = `
                <div class="bg-white rounded-xl p-6 card-shadow">
                    <div class="text-center py-12">
                        <div class="text-6xl mb-4">❌</div>
                        <p class="text-red-600 font-semibold mb-2">Error processing call</p>
                        <p class="text-sm text-gray-600">${error.message}</p>
                    </div>
                </div>
            `;
        }
    }
}

// Initialize global call player
window.callPlayer = new CallPlayer();
