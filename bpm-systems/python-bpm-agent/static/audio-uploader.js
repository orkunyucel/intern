/**
 * Audio File Uploader for Real Calls
 */

class AudioUploader {
    constructor() {
        this.currentFile = null;
    }

    initUploader() {
        const container = document.getElementById('upload-content');

        container.innerHTML = `
            <div class="max-w-2xl mx-auto">
                <!-- Upload Area -->
                <div class="bg-white rounded-xl p-8 card-shadow">
                    <div class="text-center mb-6">
                        <h2 class="text-2xl font-bold mb-2">📁 Upload Real Call Recording</h2>
                        <p class="text-gray-600">Upload an audio file for real speech-to-text processing</p>
                    </div>

                    <!-- Drag & Drop Zone -->
                    <div
                        id="drop-zone"
                        class="border-4 border-dashed border-gray-300 rounded-xl p-12 text-center hover:border-purple-500 transition-all duration-200 cursor-pointer"
                        onclick="document.getElementById('file-input').click()"
                    >
                        <div class="text-6xl mb-4">🎤</div>
                        <p class="text-lg font-semibold text-gray-700 mb-2">
                            Drop audio file here or click to browse
                        </p>
                        <p class="text-sm text-gray-500">
                            Supports: MP3, WAV, M4A, OGG (max 50MB)
                        </p>
                    </div>

                    <input
                        type="file"
                        id="file-input"
                        accept="audio/*,.mp3,.wav,.m4a,.ogg,.webm"
                        class="hidden"
                        onchange="window.audioUploader.handleFileSelect(event)"
                    />

                    <!-- Customer ID Input -->
                    <div class="mt-6">
                        <label class="block text-sm font-semibold text-gray-700 mb-2">
                            Customer ID (optional)
                        </label>
                        <input
                            type="text"
                            id="customer-id-input"
                            placeholder="CUST-12345"
                            class="w-full px-4 py-3 border-2 border-gray-300 rounded-lg focus:border-purple-500 focus:outline-none"
                        />
                    </div>

                    <!-- File Info (shown after selection) -->
                    <div id="file-info" class="hidden mt-6 p-4 bg-blue-50 rounded-lg">
                        <div class="flex items-center justify-between">
                            <div class="flex items-center gap-3">
                                <span class="text-2xl">🎵</span>
                                <div>
                                    <p id="file-name" class="font-semibold text-gray-800"></p>
                                    <p id="file-size" class="text-sm text-gray-600"></p>
                                </div>
                            </div>
                            <button
                                onclick="window.audioUploader.clearFile()"
                                class="text-red-500 hover:text-red-700"
                            >
                                ✕
                            </button>
                        </div>
                    </div>

                    <!-- Upload Button -->
                    <button
                        id="upload-btn"
                        onclick="window.audioUploader.uploadAndProcess()"
                        disabled
                        class="w-full mt-6 py-4 rounded-lg bg-gray-300 text-gray-500 font-semibold cursor-not-allowed transition-all duration-200"
                    >
                        Select a file first
                    </button>
                </div>

                <!-- Processing Results -->
                <div id="upload-results" class="mt-8"></div>
            </div>
        `;

        // Drag & drop handlers
        const dropZone = document.getElementById('drop-zone');

        dropZone.addEventListener('dragover', (e) => {
            e.preventDefault();
            dropZone.classList.add('border-purple-500', 'bg-purple-50');
        });

        dropZone.addEventListener('dragleave', () => {
            dropZone.classList.remove('border-purple-500', 'bg-purple-50');
        });

        dropZone.addEventListener('drop', (e) => {
            e.preventDefault();
            dropZone.classList.remove('border-purple-500', 'bg-purple-50');

            const files = e.dataTransfer.files;
            if (files.length > 0) {
                this.handleFile(files[0]);
            }
        });
    }

    handleFileSelect(event) {
        const file = event.target.files[0];
        if (file) {
            this.handleFile(file);
        }
    }

    handleFile(file) {
        // Validate file type
        const allowedTypes = ['audio/mpeg', 'audio/wav', 'audio/mp4', 'audio/ogg', 'audio/webm'];
        const allowedExts = ['.mp3', '.wav', '.m4a', '.ogg', '.webm'];

        const fileExt = '.' + file.name.split('.').pop().toLowerCase();

        if (!allowedTypes.includes(file.type) && !allowedExts.includes(fileExt)) {
            alert('Invalid file type. Please upload MP3, WAV, M4A, or OGG audio files.');
            return;
        }

        // Validate file size (50MB max)
        const maxSize = 50 * 1024 * 1024;
        if (file.size > maxSize) {
            alert('File too large. Maximum size is 50MB.');
            return;
        }

        this.currentFile = file;

        // Show file info
        document.getElementById('file-name').textContent = file.name;
        document.getElementById('file-size').textContent = this.formatFileSize(file.size);
        document.getElementById('file-info').classList.remove('hidden');

        // Enable upload button
        const uploadBtn = document.getElementById('upload-btn');
        uploadBtn.disabled = false;
        uploadBtn.classList.remove('bg-gray-300', 'text-gray-500', 'cursor-not-allowed');
        uploadBtn.classList.add('bg-gradient-to-r', 'from-purple-500', 'to-blue-500', 'text-white', 'hover:shadow-lg', 'cursor-pointer');
        uploadBtn.textContent = '🚀 Upload & Process with Whisper';
    }

    clearFile() {
        this.currentFile = null;
        document.getElementById('file-info').classList.add('hidden');
        document.getElementById('file-input').value = '';

        const uploadBtn = document.getElementById('upload-btn');
        uploadBtn.disabled = true;
        uploadBtn.classList.add('bg-gray-300', 'text-gray-500', 'cursor-not-allowed');
        uploadBtn.classList.remove('bg-gradient-to-r', 'from-purple-500', 'to-blue-500', 'text-white', 'hover:shadow-lg', 'cursor-pointer');
        uploadBtn.textContent = 'Select a file first';
    }

    formatFileSize(bytes) {
        if (bytes < 1024) return bytes + ' B';
        if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
        return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
    }

    async uploadAndProcess() {
        if (!this.currentFile) return;

        const resultsContainer = document.getElementById('upload-results');
        resultsContainer.innerHTML = `
            <div class="bg-white rounded-xl p-8 card-shadow">
                <div class="text-center py-12">
                    <div class="loading mx-auto mb-4"></div>
                    <p class="text-gray-600 font-semibold">Processing audio...</p>
                    <div class="mt-4 space-y-2 text-sm text-gray-500">
                        <p>⏳ Step 1: Uploading file...</p>
                        <p>⏳ Step 2: Whisper speech-to-text...</p>
                        <p>⏳ Step 3: Sentiment analysis...</p>
                        <p>⏳ Step 4: AI agent decision...</p>
                    </div>
                    <p class="mt-4 text-xs text-gray-400">This may take 10-30 seconds depending on audio length</p>
                </div>
            </div>
        `;

        try {
            const formData = new FormData();
            formData.append('file', this.currentFile);

            const customerId = document.getElementById('customer-id-input').value || 'CUST-UPLOAD';
            formData.append('customer_id', customerId);

            const response = await fetch('/api/call/upload', {
                method: 'POST',
                body: formData
            });

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.detail || 'Upload failed');
            }

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
                        <h3 class="text-lg font-bold">✅ Processing Complete</h3>
                        <span class="text-sm text-gray-500">Case ID: ${result.case_id}</span>
                    </div>

                    <!-- Uploaded File Info -->
                    <div class="bg-gray-50 rounded-lg p-4">
                        <p class="text-xs text-gray-600 font-semibold mb-2">📁 UPLOADED FILE</p>
                        <p class="text-sm font-semibold">${result.uploaded_file}</p>
                    </div>

                    <!-- Whisper Transcription -->
                    <div class="bg-blue-50 rounded-lg p-4">
                        <p class="text-sm font-semibold text-blue-600 mb-3">🎙️ WHISPER TRANSCRIPTION</p>
                        <p class="text-gray-800 mb-3">"${result.transcription.text}"</p>
                        <div class="grid grid-cols-3 gap-2 text-xs">
                            <div class="flex justify-between">
                                <span class="text-gray-600">Confidence:</span>
                                <span class="font-semibold">${(result.transcription.confidence * 100).toFixed(0)}%</span>
                            </div>
                            <div class="flex justify-between">
                                <span class="text-gray-600">Duration:</span>
                                <span class="font-semibold">${result.transcription.duration.toFixed(1)}s</span>
                            </div>
                            <div class="flex justify-between">
                                <span class="text-gray-600">Speaking Rate:</span>
                                <span class="font-semibold">${result.transcription.speaking_rate} wpm</span>
                            </div>
                        </div>
                        ${result.transcription.repetitions && result.transcription.repetitions.length > 0 ? `
                            <div class="mt-3 pt-3 border-t border-blue-200">
                                <p class="text-xs text-blue-600 font-semibold mb-2">Repeated Words:</p>
                                <div class="flex flex-wrap gap-2">
                                    ${result.transcription.repetitions.map(word => `
                                        <span class="bg-blue-100 text-blue-800 px-2 py-1 rounded text-xs">${word}</span>
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

            // Clear file for next upload
            this.clearFile();

        } catch (error) {
            resultsContainer.innerHTML = `
                <div class="bg-white rounded-xl p-6 card-shadow">
                    <div class="text-center py-12">
                        <div class="text-6xl mb-4">❌</div>
                        <p class="text-red-600 font-semibold mb-2">Upload Error</p>
                        <p class="text-sm text-gray-600">${error.message}</p>
                    </div>
                </div>
            `;
        }
    }
}

// Initialize global uploader
window.audioUploader = new AudioUploader();
