(function () {
  "use strict";

  var PLAYLIST_URL = "/live/playlist.m3u8";
  var STATUS_URL = "/status";
  var STATUS_POLL_MS = 5000;

  var audio = document.getElementById("player");
  var playButton = document.getElementById("play-button");
  var playerStatus = document.getElementById("player-status");
  var nowPlaying = document.getElementById("now-playing");
  var stationName = document.getElementById("station-name");

  function setPlayerStatus(text) {
    playerStatus.textContent = text;
  }

  function attachStream() {
    if (window.Hls && window.Hls.isSupported()) {
      var hls = new window.Hls({ liveDurationInfinity: true });
      hls.loadSource(PLAYLIST_URL);
      hls.attachMedia(audio);
      hls.on(window.Hls.Events.ERROR, function (_event, data) {
        if (data && data.fatal) {
          setPlayerStatus("Stream error: " + data.type + ". Retrying…");
          if (data.type === window.Hls.ErrorTypes.NETWORK_ERROR) {
            hls.startLoad();
          } else if (data.type === window.Hls.ErrorTypes.MEDIA_ERROR) {
            hls.recoverMediaError();
          }
        }
      });
      setPlayerStatus("Ready.");
    } else if (audio.canPlayType("application/vnd.apple.mpegurl")) {
      // Safari/iOS: native HLS support, no hls.js needed.
      audio.src = PLAYLIST_URL;
      setPlayerStatus("Ready.");
    } else {
      setPlayerStatus("This browser can't play HLS audio.");
      playButton.disabled = true;
    }
  }

  playButton.addEventListener("click", function () {
    audio.play().then(function () {
      playButton.textContent = "Pause";
      setPlayerStatus("Playing.");
    }).catch(function (error) {
      setPlayerStatus("Couldn't start playback: " + error.message);
    });
  });

  audio.addEventListener("pause", function () {
    playButton.textContent = "Play";
  });
  audio.addEventListener("playing", function () {
    playButton.textContent = "Pause";
    setPlayerStatus("Playing.");
  });

  function pollStatus() {
    fetch(STATUS_URL, { cache: "no-store" })
      .then(function (response) { return response.json(); })
      .then(function (status) {
        if (!status.isRunning) {
          nowPlaying.textContent = "Off air";
          return;
        }
        if (status.nowPlayingTitle) {
          nowPlaying.textContent = status.nowPlayingArtist
            ? status.nowPlayingTitle + " — " + status.nowPlayingArtist
            : status.nowPlayingTitle;
        } else {
          nowPlaying.textContent = "Waiting for the next track…";
        }
      })
      .catch(function () {
        nowPlaying.textContent = "Status unavailable.";
      });
  }

  attachStream();
  pollStatus();
  setInterval(pollStatus, STATUS_POLL_MS);
})();
