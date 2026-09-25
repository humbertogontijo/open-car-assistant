/**
 * DVR playback façade — wall-clock play/pause/speed live in camera-player.js
 * together with the transport UI. Day/timeline math lives in dvr-timeline.js.
 *
 * Re-export the playback API so callers can import from a stable module path.
 */
export {
  playAt,
  playRecording,
  stopRecordingPlayback,
  togglePlaybackPause,
  seekPlaybackSeconds,
  backToLive,
  applyCameraPlayerSrc,
  isRecordingPlayback,
  canPlayRecording,
} from "./camera-player.js";
