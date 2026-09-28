(function () {
  let currentRange = 'TODAY';
  let isSubscribed = false;

  const elements = {
    providerBadge: document.getElementById('providerBadge'),
    permissionBadge: document.getElementById('permissionBadge'),
    providerSelect: document.getElementById('providerSelect'),
    btnRequestPermission: document.getElementById('btnRequestPermission'),
    btnRefresh: document.getElementById('btnRefresh'),
    errorBanner: document.getElementById('errorBanner'),
    errorMessage: document.getElementById('errorMessage'),
    btnErrorAction: document.getElementById('btnErrorAction'),
    rangeButtons: document.querySelectorAll('.range-btn'),
    metricSteps: document.getElementById('metricSteps'),
    metricCalories: document.getElementById('metricCalories'),
    metricDistance: document.getElementById('metricDistance'),
    metricHeartPoints: document.getElementById('metricHeartPoints'),
    metricMoveMinutes: document.getElementById('metricMoveMinutes'),
    metricSleep: document.getElementById('metricSleep'),
    subSteps: document.getElementById('subSteps'),
    subDistance: document.getElementById('subDistance'),
    subSleep: document.getElementById('subSleep'),
    stepChart: document.getElementById('stepChart'),
    dailyTableBody: document.getElementById('dailyTableBody'),
    recordingStatus: document.getElementById('recordingStatus'),
    btnToggleRecording: document.getElementById('btnToggleRecording'),
    lastUpdated: document.getElementById('lastUpdated')
  };

  // Safe wrapper for calling Android native bridge
  function callNative(funcName, ...args) {
    if (window.AndroidFitnessBridge && typeof window.AndroidFitnessBridge[funcName] === 'function') {
      try {
        window.AndroidFitnessBridge[funcName](...args);
      } catch (err) {
        console.error('Bridge call error:', err);
      }
    } else {
      console.warn('AndroidFitnessBridge.' + funcName + ' not available');
    }
  }

  // Initial load
  document.addEventListener('DOMContentLoaded', function () {
    setupEventListeners();
    callNative('checkProviderAvailability');
    callNative('checkFitnessPermissions');
    fetchData(currentRange);
  });

  function setupEventListeners() {
    elements.btnRefresh.addEventListener('click', function () {
      fetchData(currentRange);
    });

    elements.btnRequestPermission.addEventListener('click', function () {
      callNative('requestFitnessPermissions');
    });

    elements.providerSelect.addEventListener('change', function (e) {
      const selected = e.target.value;
      callNative('selectProvider', selected);
      setTimeout(() => fetchData(currentRange), 300);
    });

    elements.rangeButtons.forEach(btn => {
      btn.addEventListener('click', function () {
        elements.rangeButtons.forEach(b => b.classList.remove('active'));
        this.classList.add('active');
        currentRange = this.getAttribute('data-range');
        fetchData(currentRange);
      });
    });

    elements.btnToggleRecording.addEventListener('click', function () {
      if (isSubscribed) {
        callNative('unsubscribeFromFitnessRecording');
      } else {
        callNative('subscribeToFitnessRecording');
      }
    });

    elements.btnErrorAction.addEventListener('click', function () {
      callNative('requestFitnessPermissions');
    });
  }

  function fetchData(range) {
    const requestJson = JSON.stringify({ range: range });
    callNative('getFitnessData', requestJson);
  }

  // --- NATIVE CALLBACKS ---

  window.onFitnessDataReceived = function (payload) {
    elements.errorBanner.style.display = 'none';
    elements.lastUpdated.textContent = new Date().toLocaleTimeString();

    if (!payload || !payload.data) return;

    const summary = payload.data.summary || {};
    const days = payload.data.days || [];

    if (payload.provider) {
      elements.providerBadge.textContent = payload.provider;
    }

    renderSummary(summary);
    renderDailyTable(days);
    renderChart(days);
  };

  window.onFitnessError = function (errorPayload) {
    if (!errorPayload) return;
    elements.errorMessage.textContent = errorPayload.message || 'An error occurred reading fitness data.';
    elements.errorBanner.style.display = 'flex';

    if (errorPayload.errorCode === 'PERMISSION_DENIED') {
      elements.permissionBadge.textContent = 'Denied';
      elements.permissionBadge.className = 'badge badge-danger';
    }
  };

  window.onPermissionStatus = function (statusPayload) {
    if (!statusPayload) return;
    if (statusPayload.isGranted || statusPayload.success) {
      elements.permissionBadge.textContent = 'Connected';
      elements.permissionBadge.className = 'badge badge-success';
      elements.errorBanner.style.display = 'none';
      fetchData(currentRange);
    } else {
      elements.permissionBadge.textContent = 'Not Granted';
      elements.permissionBadge.className = 'badge badge-danger';
    }
  };

  window.onAvailabilityStatus = function (availabilityPayload) {
    if (!availabilityPayload) return;
    const isAvail = availabilityPayload.availability === 'AVAILABLE' || availabilityPayload.success;
    if (isAvail) {
      elements.providerBadge.textContent = availabilityPayload.provider || 'Available';
      elements.providerBadge.className = 'badge badge-success';
    } else {
      elements.providerBadge.textContent = availabilityPayload.availability || 'Unavailable';
      elements.providerBadge.className = 'badge badge-warning';
    }
  };

  window.onSubscriptionStatus = function (subPayload) {
    if (!subPayload) return;
    isSubscribed = subPayload.isSubscribed;
    elements.recordingStatus.textContent = subPayload.message || (isSubscribed ? 'Active' : 'Inactive');
    elements.btnToggleRecording.textContent = isSubscribed ? 'Unsubscribe' : 'Subscribe';
    elements.btnToggleRecording.className = isSubscribed ? 'btn btn-secondary' : 'btn btn-primary';
  };

  // --- RENDERING HELPERS ---

  function renderSummary(summary) {
    // Steps
    if (summary.steps != null) {
      elements.metricSteps.textContent = summary.steps.toLocaleString();
      const pct = Math.min(100, Math.round((summary.steps / 10000) * 100));
      elements.subSteps.textContent = pct + '% of 10,000 goal';
    } else {
      elements.metricSteps.textContent = 'N/A';
      elements.subSteps.textContent = 'No data available';
    }

    // Calories
    if (summary.caloriesKcal != null) {
      elements.metricCalories.textContent = summary.caloriesKcal.toFixed(1);
    } else {
      elements.metricCalories.textContent = 'N/A';
    }

    // Distance
    if (summary.distanceMeters != null) {
      const km = (summary.distanceMeters / 1000).toFixed(2);
      elements.metricDistance.textContent = km;
      elements.subDistance.textContent = summary.distanceMeters.toFixed(0) + ' m';
    } else {
      elements.metricDistance.textContent = 'N/A';
      elements.subDistance.textContent = 'km';
    }

    // Heart Points
    if (summary.heartPoints != null) {
      elements.metricHeartPoints.textContent = summary.heartPoints.toFixed(1);
    } else {
      elements.metricHeartPoints.textContent = 'N/A';
    }

    // Move Minutes
    if (summary.moveMinutes != null) {
      elements.metricMoveMinutes.textContent = summary.moveMinutes;
    } else {
      elements.metricMoveMinutes.textContent = 'N/A';
    }

    // Sleep
    if (summary.sleepDurationMinutes != null) {
      const hours = (summary.sleepDurationMinutes / 60).toFixed(1);
      elements.metricSleep.textContent = hours;
      elements.subSleep.textContent = summary.sleepDurationMinutes + ' mins total';
    } else {
      elements.metricSleep.textContent = 'N/A';
      elements.subSleep.textContent = 'hours';
    }
  }

  function renderDailyTable(days) {
    if (!days || days.length === 0) {
      elements.dailyTableBody.innerHTML = '<tr><td colspan="7" style="text-align:center;">No fitness records found.</td></tr>';
      return;
    }

    let html = '';
    days.forEach(day => {
      const dateStr = day.date || '';
      const steps = (day.steps && day.steps.value != null) ? day.steps.value.toLocaleString() : '-';
      const calories = (day.calories && day.calories.value != null) ? day.calories.value.toFixed(0) : '-';
      const distance = (day.distanceMeters && day.distanceMeters.value != null) ? (day.distanceMeters.value / 1000).toFixed(2) + ' km' : '-';
      const hp = (day.heartPoints && day.heartPoints.value != null) ? day.heartPoints.value.toFixed(1) : '-';
      const move = (day.moveMinutes && day.moveMinutes.value != null) ? day.moveMinutes.value + 'm' : '-';
      const sleep = (day.sleepDurationMinutes && day.sleepDurationMinutes.value != null) ? (day.sleepDurationMinutes.value / 60).toFixed(1) + 'h' : '-';

      html += `
        <tr>
          <td><strong>${dateStr}</strong></td>
          <td>${steps}</td>
          <td>${calories}</td>
          <td>${distance}</td>
          <td>${hp}</td>
          <td>${move}</td>
          <td>${sleep}</td>
        </tr>
      `;
    });

    elements.dailyTableBody.innerHTML = html;
  }

  function renderChart(days) {
    const canvas = elements.stepChart;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    const width = canvas.width = canvas.parentElement.clientWidth || 300;
    const height = canvas.height = 160;

    ctx.clearRect(0, 0, width, height);

    if (!days || days.length === 0) {
      ctx.fillStyle = '#94a3b8';
      ctx.font = '12px sans-serif';
      ctx.fillText('No activity data to chart', width / 3, height / 2);
      return;
    }

    const maxSteps = Math.max(10000, ...days.map(d => (d.steps && d.steps.value) || 0));
    const padding = 30;
    const chartWidth = width - padding * 2;
    const chartHeight = height - padding * 2;
    const barWidth = Math.max(8, (chartWidth / days.length) - 8);

    days.forEach((day, i) => {
      const steps = (day.steps && day.steps.value) || 0;
      const barHeight = (steps / maxSteps) * chartHeight;
      const x = padding + i * (barWidth + 8);
      const y = height - padding - barHeight;

      // Draw bar
      ctx.fillStyle = '#3b82f6';
      ctx.beginPath();
      ctx.roundRect ? ctx.roundRect(x, y, barWidth, barHeight, [4, 4, 0, 0]) : ctx.rect(x, y, barWidth, barHeight);
      ctx.fill();

      // Draw label below
      const label = day.date ? day.date.slice(5) : '';
      ctx.fillStyle = '#64748b';
      ctx.font = '10px sans-serif';
      ctx.fillText(label, x, height - 10);
    });
  }

})();
