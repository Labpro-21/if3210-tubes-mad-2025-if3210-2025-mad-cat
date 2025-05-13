// Updated stopAndClearCurrentSong method for MusicViewModel.kt
// Replace the existing method with this one:

    // Stop playing and clear current song (for deletion/logout)
    fun stopAndClearCurrentSong() {
        try {
            // Stop the service completely
            context?.let { ctx ->
                val intent = Intent(ctx, MediaPlaybackService::class.java)
                intent.action = "ACTION_STOP"
                ctx.stopService(intent)
                
                // Also unbind from the service if bound
                if (isServiceBound) {
                    ctx.unbindService(serviceConnection)
                    isServiceBound = false
                }
            }
            
            // Clear the media service reference
            mediaService = null
            
            // Also release the local media player if any
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
            
            // Cancel any ongoing updates
            updateJob?.cancel()
            updateJob = null
    
            // Reset all playback state
            _isPlaying.value = false
            _currentPosition.value = 0
            _duration.value = 0
            _currentSong.value = null
            currentPlaylist = emptyList()
            currentIndex = 0
            
            // Clear playlist contexts
            onlinePlaylist = emptyList()
            onlinePlaylistType = ""
            
        } catch (e: Exception) {
            Log.e("MusicViewModel", "Error in stopAndClearCurrentSong", e)
        }
    }
