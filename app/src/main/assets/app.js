(function () {
  const DEFAULT_GOAL = 10000;
  const GOAL_MIN = 1000;
  const GOAL_MAX = 50000;
  const GOAL_STEP = 500;
  const GOAL_KEY = 'stepGoal';
  const RING_LENGTH = 326.73; // circumference of the r=52 progress ring
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  let currentRange = 'TODAY';
  let periodOffset = 0; // 0 = current week or month, -1 = the one before, and so on
  let stepGoal = loadGoal();
  let goalDraft = stepGoal;
  let lastPayload = null;
  let hasPermission = false;
  let isAvailable = true;

  const elements = {
    welcomeScreen: document.getElementById('welcomeScreen'),
    btnFetchStart: document.getElementById('btnFetchStart'),
    dashboardContainer: document.getElementById('dashboardContainer'),
    greeting: document.getElementById('greeting'),
    statusDot: document.getElementById('statusDot'),
    statusText: document.getElementById('statusText'),
    lastUpdated: document.getElementById('lastUpdated'),
    btnRefresh: document.getElementById('btnRefresh'),
    btnBack: document.getElementById('btnBack'),
    periodNav: document.getElementById('periodNav'),
    btnPrevPeriod: document.getElementById('btnPrevPeriod'),
    btnNextPeriod: document.getElementById('btnNextPeriod'),
    periodTitle: document.getElementById('periodTitle'),
    periodDates: document.getElementById('periodDates'),
    btnEditGoal: document.getElementById('btnEditGoal'),
    goalValue: document.getElementById('goalValue'),
    goalSheet: document.getElementById('goalSheet'),
    goalDraft: document.getElementById('goalDraft'),
    btnGoalDown: document.getElementById('btnGoalDown'),
    btnGoalUp: document.getElementById('btnGoalUp'),
    btnGoalCancel: document.getElementById('btnGoalCancel'),
    btnGoalSave: document.getElementById('btnGoalSave'),
    goalPresets: document.querySelectorAll('.goal-presets button'),
    notice: document.getElementById('notice'),
    rangeThumb: document.getElementById('rangeThumb'),
    rangeButtons: document.querySelectorAll('.range-btn'),
    hero: document.getElementById('hero'),
    stepsRing: document.getElementById('stepsRing'),
    metricSteps: document.getElementById('metricSteps'),
    stepsPercent: document.getElementById('stepsPercent'),
    stepsCaption: document.getElementById('stepsCaption'),
    subSteps: document.getElementById('subSteps'),
    highlights: document.getElementById('highlights'),
    bestDay: document.getElementById('bestDay'),
    bestDaySteps: document.getElementById('bestDaySteps'),
    goalDays: document.getElementById('goalDays'),
    goalDaysTotal: document.getElementById('goalDaysTotal'),
    goalDots: document.getElementById('goalDots'),
    metricCalories: document.getElementById('metricCalories'),
    metricDistance: document.getElementById('metricDistance'),
    metricMoveMinutes: document.getElementById('metricMoveMinutes'),
    metricSleep: document.getElementById('metricSleep'),
    sleepTitle: document.getElementById('sleepTitle'),
    sparkCalories: document.getElementById('sparkCalories'),
    sparkDistance: document.getElementById('sparkDistance'),
    sparkMove: document.getElementById('sparkMove'),
    sparkSleep: document.getElementById('sparkSleep'),
    activityChart: document.getElementById('activityChart'),
    chartDetail: document.getElementById('chartDetail'),
    dailyList: document.getElementById('dailyList')
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

  // Initial setup
  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.reveal').forEach((el, i) => el.style.setProperty('--i', i));
    setGreeting();
    elements.goalValue.textContent = stepGoal.toLocaleString();
    setupEventListeners();
  });

  function setGreeting() {
    const hour = new Date().getHours();
    elements.greeting.textContent = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  }

  function setupEventListeners() {
    elements.btnFetchStart.addEventListener('click', function () {
      elements.welcomeScreen.style.display = 'none';
      elements.dashboardContainer.style.display = 'block';

      callNative('checkProviderAvailability');
      // Data is fetched once the permission status comes back
      callNative('checkFitnessPermissions');
    });

    elements.btnBack.addEventListener('click', function () {
      closeGoalSheet();
      elements.dashboardContainer.style.display = 'none';
      elements.welcomeScreen.style.display = 'block';
    });

    // Refresh doubles as the way back in when access was declined
    elements.btnRefresh.addEventListener('click', function () {
      if (hasPermission) {
        fetchData(currentRange);
      } else {
        callNative('requestFitnessPermissions');
      }
    });

    elements.rangeButtons.forEach((btn, index) => {
      btn.addEventListener('click', function () {
        elements.rangeButtons.forEach(b => b.classList.remove('active'));
        this.classList.add('active');
        elements.rangeThumb.style.transform = 'translateX(' + (index * 100) + '%)';
        currentRange = this.getAttribute('data-range');
        periodOffset = 0;
        updatePeriodNav();
        fetchData(currentRange);
      });
    });

    elements.btnPrevPeriod.addEventListener('click', () => shiftPeriod(-1));
    elements.btnNextPeriod.addEventListener('click', () => shiftPeriod(1));

    elements.btnEditGoal.addEventListener('click', openGoalSheet);
    elements.btnGoalDown.addEventListener('click', () => setGoalDraft(goalDraft - GOAL_STEP));
    elements.btnGoalUp.addEventListener('click', () => setGoalDraft(goalDraft + GOAL_STEP));
    elements.goalPresets.forEach(btn => {
      btn.addEventListener('click', () => setGoalDraft(Number(btn.getAttribute('data-goal'))));
    });
    elements.btnGoalCancel.addEventListener('click', closeGoalSheet);
    elements.btnGoalSave.addEventListener('click', saveGoal);
    elements.goalSheet.addEventListener('click', function (event) {
      if (event.target === elements.goalSheet) closeGoalSheet();
    });

    elements.activityChart.addEventListener('click', onChartTap);
  }

  function fetchData(range) {
    elements.btnRefresh.classList.add('loading');
    const requestJson = JSON.stringify({ range: range, offset: periodOffset });
    callNative('getFitnessData', requestJson);
  }

  // --- PREVIOUS WEEKS AND MONTHS ---

  function shiftPeriod(delta) {
    const next = periodOffset + delta;
    if (next > 0) return;
    periodOffset = next;
    updatePeriodNav();
    fetchData(currentRange);
  }

  function updatePeriodNav() {
    if (currentRange === 'TODAY') {
      elements.periodNav.style.display = 'none';
      return;
    }
    elements.periodNav.style.display = 'flex';
    elements.btnNextPeriod.disabled = periodOffset === 0;

    const unit = currentRange === 'WEEK' ? 'week' : 'month';
    elements.periodTitle.textContent = periodOffset === 0 ? 'This ' + unit
      : periodOffset === -1 ? 'Last ' + unit
      : -periodOffset + ' ' + unit + 's ago';

    const now = new Date();
    if (currentRange === 'WEEK') {
      // Weeks run Monday to Sunday, matching the native range
      const mondayOffset = (now.getDay() + 6) % 7;
      const monday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - mondayOffset + periodOffset * 7);
      const sunday = new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() + 6);
      const short = d => d.toLocaleDateString([], { day: 'numeric', month: 'short' });
      elements.periodDates.textContent = short(monday) + ' – ' + short(sunday);
    } else {
      const month = new Date(now.getFullYear(), now.getMonth() + periodOffset, 1);
      elements.periodDates.textContent = month.toLocaleDateString([], { month: 'long', year: 'numeric' });
    }
  }

  // --- DAILY GOAL ---

  function loadGoal() {
    try {
      const saved = parseInt(localStorage.getItem(GOAL_KEY), 10);
      if (saved >= GOAL_MIN && saved <= GOAL_MAX) return saved;
    } catch (err) {
      console.warn('Could not read saved goal:', err);
    }
    return DEFAULT_GOAL;
  }

  function setGoalDraft(value) {
    goalDraft = Math.max(GOAL_MIN, Math.min(GOAL_MAX, value));
    elements.goalDraft.textContent = goalDraft.toLocaleString();
    elements.btnGoalDown.disabled = goalDraft <= GOAL_MIN;
    elements.btnGoalUp.disabled = goalDraft >= GOAL_MAX;
    elements.goalPresets.forEach(btn => {
      btn.classList.toggle('active', Number(btn.getAttribute('data-goal')) === goalDraft);
    });
  }

  function openGoalSheet() {
    setGoalDraft(stepGoal);
    elements.goalSheet.style.display = 'flex';
  }

  function closeGoalSheet() {
    elements.goalSheet.style.display = 'none';
  }

  function saveGoal() {
    stepGoal = goalDraft;
    try {
      localStorage.setItem(GOAL_KEY, String(stepGoal));
    } catch (err) {
      console.warn('Could not save goal:', err);
    }
    elements.goalValue.textContent = stepGoal.toLocaleString();
    closeGoalSheet();
    render();
  }

  function showNotice(message) {
    elements.notice.textContent = message;
    elements.notice.style.display = 'block';
  }

  function hideNotice() {
    elements.notice.style.display = 'none';
  }

  function setStatus(connected, text) {
    elements.statusDot.classList.toggle('on', connected);
    elements.statusText.textContent = text;
  }

  // --- NATIVE CALLBACKS ---

  window.onFitnessDataReceived = function (payload) {
    elements.btnRefresh.classList.remove('loading');

    if (!payload || !payload.data) return;
    // Ignore a late response for a range the user has already switched away from
    if (payload.range && payload.range !== currentRange) return;
    if (payload.offset != null && payload.offset !== periodOffset) return;

    elements.lastUpdated.textContent = 'Updated ' +
      new Date().toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });

    lastPayload = payload;
    render();
  };

  // Draws the most recent data; also used to redraw after the goal changes
  function render() {
    if (!lastPayload) return;
    const payload = lastPayload;

    const summary = payload.data.summary || {};
    const today = toDateKey(new Date());
    const days = payload.data.days || [];
    const elapsedDays = days.filter(d => d.date && d.date <= today);

    renderSummary(summary, elapsedDays);
    renderHighlights(days, elapsedDays, today);
    renderSparklines(elapsedDays);
    renderDailyList(elapsedDays, today);
    renderActivityChart(days, today);
  }

  window.onFitnessError = function (errorPayload) {
    elements.btnRefresh.classList.remove('loading');
    if (!errorPayload) return;
    showNotice(errorPayload.message || 'Something went wrong reading your fitness data.');
  };

  window.onPermissionStatus = function (statusPayload) {
    if (!statusPayload || !isAvailable) return;
    hasPermission = !!(statusPayload.isGranted || statusPayload.success);
    if (hasPermission) {
      setStatus(true, 'Health Connect');
      hideNotice();
      fetchData(currentRange);
    } else {
      setStatus(false, 'Not connected');
      showNotice('Health Connect access is off. Tap refresh to allow access, or turn it on in Health Connect settings.');
    }
  };

  window.onAvailabilityStatus = function (availabilityPayload) {
    if (!availabilityPayload) return;
    isAvailable = availabilityPayload.availability === 'AVAILABLE';
    if (!isAvailable) {
      setStatus(false, 'Not connected');
      showNotice(availabilityPayload.availability === 'NOT_INSTALLED'
        ? 'Health Connect needs to be installed or updated on this device.'
        : 'Health Connect is not supported on this device.');
    }
  };

  // --- HELPERS ---

  function toDateKey(date) {
    const pad = n => String(n).padStart(2, '0');
    return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());
  }

  function parseDateKey(key) {
    const parts = key.split('-').map(Number);
    return new Date(parts[0], parts[1] - 1, parts[2]);
  }

  function metric(day, key) {
    return (day[key] && day[key].value != null) ? day[key].value : null;
  }

  function formatDuration(minutes) {
    minutes = Math.round(minutes);
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    if (h === 0) return m + 'm';
    return m === 0 ? h + 'h' : h + 'h ' + m + 'm';
  }

  function formatInt(value) {
    return Math.round(value).toLocaleString();
  }

  function easeOut(t) {
    return 1 - Math.pow(1 - t, 3);
  }

  // Counts a number up (or down) from the value the element last showed
  const numberState = new WeakMap();

  function animateNumber(el, to, format) {
    const state = numberState.get(el) || { value: 0, run: 0 };
    const from = state.value;
    const run = state.run + 1;
    numberState.set(el, { value: to == null ? 0 : to, run: run });

    if (to == null) {
      el.textContent = '--';
      return;
    }
    if (reduceMotion || from === to) {
      el.textContent = format(to);
      return;
    }

    const start = performance.now();
    function tick(now) {
      if (numberState.get(el).run !== run) return; // superseded by a newer value
      const t = Math.min(1, (now - start) / 900);
      el.textContent = format(from + (to - from) * easeOut(t));
      if (t < 1) requestAnimationFrame(tick);
    }
    requestAnimationFrame(tick);
  }

  function dayLabel(dateKey, todayKey) {
    if (dateKey === todayKey) return 'Today';
    const date = parseDateKey(dateKey);
    const daysAgo = Math.round((parseDateKey(todayKey) - date) / 86400000);
    if (daysAgo === 1) return 'Yesterday';
    if (daysAgo < 7) return date.toLocaleDateString([], { weekday: 'long' });
    return date.toLocaleDateString([], { day: 'numeric', month: 'short' });
  }

  // --- RENDERING ---

  function renderSummary(summary, elapsedDays) {
    const dayCount = Math.max(1, elapsedDays.length);
    const isMultiDay = dayCount > 1;

    // Steps: progress is measured against the daily goal, using the daily average for longer ranges
    const steps = summary.steps;
    const average = steps != null ? Math.round(steps / dayCount) : 0;
    const pct = Math.min(100, Math.round((average / stepGoal) * 100));

    animateNumber(elements.metricSteps, steps, formatInt);
    animateNumber(elements.stepsPercent, pct, v => Math.round(v) + '%');
    elements.stepsRing.style.strokeDashoffset = RING_LENGTH * (1 - pct / 100);
    elements.hero.classList.toggle('goal-hit', pct >= 100);
    elements.stepsCaption.textContent = isMultiDay ? 'of daily goal, on average' : 'of daily goal';

    if (steps == null) {
      elements.subSteps.textContent = 'No step data yet';
    } else if (isMultiDay) {
      elements.subSteps.textContent = average.toLocaleString() + ' steps a day';
    } else if (steps >= stepGoal) {
      elements.subSteps.textContent = 'Goal reached. Great work!';
    } else if (steps === 0) {
      elements.subSteps.textContent = 'Take your first steps today';
    } else {
      elements.subSteps.textContent = (stepGoal - steps).toLocaleString() + ' steps to go';
    }

    animateNumber(elements.metricCalories, summary.caloriesKcal, formatInt);
    animateNumber(elements.metricDistance,
      summary.distanceMeters != null ? summary.distanceMeters / 1000 : null, v => v.toFixed(2));
    animateNumber(elements.metricMoveMinutes, summary.moveMinutes, formatInt);

    // Sleep: a nightly average reads better than a multi-day total
    const nights = elapsedDays.filter(d => metric(d, 'sleepDurationMinutes') > 0).length;
    elements.sleepTitle.textContent = isMultiDay ? 'Sleep (avg / night)' : 'Sleep';
    let sleep = summary.sleepDurationMinutes > 0 ? summary.sleepDurationMinutes : null;
    if (sleep != null && isMultiDay && nights > 0) sleep = sleep / nights;
    animateNumber(elements.metricSleep, sleep, formatDuration);
  }

  // Best day and goal streak, only meaningful for week and month
  function renderHighlights(days, elapsedDays, todayKey) {
    if (elapsedDays.length < 2) {
      elements.highlights.style.display = 'none';
      return;
    }
    elements.highlights.style.display = 'grid';

    let best = null;
    elapsedDays.forEach(day => {
      const steps = metric(day, 'steps') || 0;
      if (steps > 0 && (!best || steps > metric(best, 'steps'))) best = day;
    });
    elements.bestDay.textContent = best ? dayLabel(best.date, todayKey) : '--';
    elements.bestDaySteps.textContent = best ? metric(best, 'steps').toLocaleString() + ' steps' : 'No steps yet';

    const goalDays = elapsedDays.filter(d => metric(d, 'steps') >= stepGoal).length;
    animateNumber(elements.goalDays, goalDays, formatInt);
    elements.goalDaysTotal.textContent = ' of ' + elapsedDays.length + ' days';

    // One dot per day in the range: filled when the goal was met, hollow for days still to come
    elements.goalDots.innerHTML = days.map((day, i) => {
      const state = day.date > todayKey ? '' : (metric(day, 'steps') >= stepGoal ? 'hit' : 'miss');
      return `<span class="${state}" style="--i: ${i}"></span>`;
    }).join('');
  }

  function renderSparkline(svg, values) {
    const max = Math.max(...values);
    // Nothing worth drawing for a single day or an all-zero series
    if (values.length < 2 || max <= 0) {
      svg.classList.remove('on');
      return;
    }

    const points = values.map((v, i) => {
      const x = (i / (values.length - 1)) * 100;
      const y = 26 - (v / max) * 24;
      return x.toFixed(1) + ' ' + y.toFixed(1);
    });
    const line = 'M' + points.join(' L');
    svg.innerHTML = `<path class="area" d="${line} L100 28 L0 28 Z"/><path class="line" d="${line}"/>`;

    // Restart the reveal animation
    svg.classList.remove('on');
    void svg.getBoundingClientRect();
    svg.classList.add('on');
  }

  function renderSparklines(elapsedDays) {
    const series = key => elapsedDays.map(d => metric(d, key) || 0);
    renderSparkline(elements.sparkCalories, series('calories'));
    renderSparkline(elements.sparkDistance, series('distanceMeters'));
    renderSparkline(elements.sparkMove, series('moveMinutes'));
    renderSparkline(elements.sparkSleep, series('sleepDurationMinutes'));
  }

  function renderDailyList(days, todayKey) {
    if (!days || days.length === 0) {
      elements.dailyList.innerHTML = '<li class="day-empty">No fitness records found.</li>';
      return;
    }

    const check = '<svg class="day-check" viewBox="0 0 24 24"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>';
    let html = '';
    // Most recent day first
    days.slice().reverse().forEach((day, i) => {
      const steps = metric(day, 'steps');
      const calories = metric(day, 'calories');
      const distance = metric(day, 'distanceMeters');
      const move = metric(day, 'moveMinutes');
      const sleep = metric(day, 'sleepDurationMinutes');

      const meta = [];
      if (calories > 0) meta.push(formatInt(calories) + ' kcal');
      if (distance > 0) meta.push((distance / 1000).toFixed(2) + ' km');
      if (move > 0) meta.push(move + ' min active');
      if (sleep > 0) meta.push(formatDuration(sleep) + ' sleep');

      const pct = Math.min(100, Math.round(((steps || 0) / stepGoal) * 100));

      html += `
        <li class="day-row" style="--i: ${Math.min(i, 8)}">
          <div class="day-main">
            <span class="day-name">${dayLabel(day.date, todayKey)}${steps >= stepGoal ? check : ''}</span>
            <span class="day-steps"><strong>${steps != null ? steps.toLocaleString() : '--'}</strong> steps</span>
          </div>
          <div class="day-bar"><span data-width="${pct}"></span></div>
          <div class="day-meta">${meta.length ? meta.map(m => `<span>${m}</span>`).join('') : '<span class="none">No other activity recorded</span>'}</div>
        </li>
      `;
    });

    elements.dailyList.innerHTML = html;

    // Set widths on the next frame so the bars animate in from zero
    requestAnimationFrame(() => {
      elements.dailyList.querySelectorAll('.day-bar span').forEach(bar => {
        bar.style.width = bar.getAttribute('data-width') + '%';
      });
    });
  }

  // --- VISUALIZATION: Animated bar chart of daily steps ---

  const chart = { days: [], today: '', selected: -1, progress: 1, frame: 0, paddingLeft: 28, slot: 0 };

  function renderActivityChart(days, todayKey) {
    chart.days = days || [];
    chart.today = todayKey;
    chart.selected = -1;
    updateChartDetail();

    cancelAnimationFrame(chart.frame);
    if (reduceMotion) {
      chart.progress = 1;
      drawChart();
      return;
    }

    // Bars grow up from the baseline
    const start = performance.now();
    function step(now) {
      const t = Math.min(1, (now - start) / 800);
      chart.progress = easeOut(t);
      drawChart();
      if (t < 1) chart.frame = requestAnimationFrame(step);
    }
    chart.frame = requestAnimationFrame(step);
  }

  function onChartTap(event) {
    if (!chart.slot) return;
    const rect = elements.activityChart.getBoundingClientRect();
    const index = Math.floor((event.clientX - rect.left - chart.paddingLeft) / chart.slot);
    const day = chart.days[index];
    if (!day || day.date > chart.today) return;

    chart.selected = chart.selected === index ? -1 : index;
    updateChartDetail();
    drawChart();
  }

  function updateChartDetail() {
    const day = chart.days[chart.selected];
    elements.chartDetail.textContent = day
      ? dayLabel(day.date, chart.today) + ' · ' + (metric(day, 'steps') || 0).toLocaleString() + ' steps'
      : 'Tap a bar for details';
  }

  function roundedBar(ctx, x, y, width, height, radius) {
    ctx.beginPath();
    if (ctx.roundRect) {
      ctx.roundRect(x, y, width, height, Math.min(radius, height / 2));
    } else {
      ctx.rect(x, y, width, height);
    }
    ctx.fill();
  }

  function drawChart() {
    const canvas = elements.activityChart;
    const days = chart.days;

    const dpr = window.devicePixelRatio || 1;
    const width = canvas.clientWidth || 280;
    const height = canvas.clientHeight || 170;
    canvas.width = width * dpr;
    canvas.height = height * dpr;

    const ctx = canvas.getContext('2d');
    ctx.scale(dpr, dpr);
    ctx.clearRect(0, 0, width, height);
    ctx.font = '10px sans-serif';

    if (days.length === 0) {
      ctx.fillStyle = '#6e787e';
      ctx.textAlign = 'center';
      ctx.fillText('No activity data available', width / 2, height / 2);
      return;
    }

    const maxSteps = Math.max(stepGoal * 1.2, ...days.map(d => metric(d, 'steps') || 0));
    const paddingLeft = chart.paddingLeft;
    const paddingBottom = 22;
    const paddingTop = 8;
    const chartHeight = height - paddingBottom - paddingTop;
    const baseline = height - paddingBottom;
    const slot = (width - paddingLeft) / days.length;
    const barWidth = Math.max(3, Math.min(26, slot * 0.58));
    const radius = barWidth / 2;
    chart.slot = slot;

    const gradient = ctx.createLinearGradient(0, paddingTop, 0, baseline);
    gradient.addColorStop(0, '#3bb896');
    gradient.addColorStop(1, '#10847e');

    const labelEvery = Math.ceil(days.length / 7);

    days.forEach((day, i) => {
      const steps = metric(day, 'steps') || 0;
      const barHeight = (steps / maxSteps) * chartHeight * chart.progress;
      const x = paddingLeft + i * slot + (slot - barWidth) / 2;
      const isSelected = i === chart.selected;
      const isToday = day.date === chart.today;

      // Faint full-height track behind each bar
      ctx.fillStyle = '#e6fcf6';
      roundedBar(ctx, x, paddingTop, barWidth, chartHeight, radius);

      if (barHeight > 0) {
        ctx.globalAlpha = chart.selected >= 0 && !isSelected ? 0.4 : 1;
        ctx.fillStyle = isSelected ? '#0d4f44' : gradient;
        roundedBar(ctx, x, baseline - barHeight, barWidth, barHeight, radius);
        ctx.globalAlpha = 1;
      }

      // X labels: weekday for short ranges, day of month for longer ones
      if (day.date && i % labelEvery === 0) {
        const date = parseDateKey(day.date);
        const label = days.length <= 7
          ? date.toLocaleDateString([], { weekday: 'short' })
          : String(date.getDate());
        ctx.fillStyle = isToday || isSelected ? '#10847e' : '#6e787e';
        ctx.font = (isToday || isSelected ? 'bold ' : '') + '10px sans-serif';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'alphabetic';
        ctx.fillText(label, x + barWidth / 2, height - 5);
      }
    });

    // Daily goal line
    const goalY = baseline - (stepGoal / maxSteps) * chartHeight;
    ctx.strokeStyle = '#10847e';
    ctx.lineWidth = 1;
    ctx.setLineDash([3, 4]);
    ctx.beginPath();
    ctx.moveTo(paddingLeft, goalY);
    ctx.lineTo(width, goalY);
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.font = '10px sans-serif';
    ctx.fillStyle = '#6e787e';
    ctx.textAlign = 'left';
    ctx.textBaseline = 'middle';
    ctx.fillText(+(stepGoal / 1000).toFixed(1) + 'k', 0, goalY);
  }

})();
