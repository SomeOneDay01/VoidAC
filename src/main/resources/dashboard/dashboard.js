(function() {
  const token = new URLSearchParams(location.search).get('token') || '';
  function api(path) { return path + (token ? (path.includes('?')?'&':'?') + 'token=' + encodeURIComponent(token) : ''); }

  function updateTime() { document.getElementById('navTime').textContent = new Date().toLocaleString(); }
  setInterval(updateTime, 1000); updateTime();

  async function fetchJSON(url) { const r = await fetch(url); if(!r.ok) throw new Error(r.status); return r.json(); }

  async function loadOverview() {
    try {
      const data = await fetchJSON(api('/api/stats'));
      document.getElementById('stat-online').textContent = data.online_players + '/' + data.max_players;
      document.getElementById('stat-tracked').textContent = data.tracked_players;
      document.getElementById('stat-bans').textContent = data.total_bans;
      document.getElementById('stat-tps').textContent = data.tps ? data.tps.join(', ') : '-';

      const tbody = document.querySelector('#topTable tbody');
      tbody.innerHTML = '';
      if (data.top_confidence && data.top_confidence.length) {
        data.top_confidence.forEach((p, i) => {
          const tr = document.createElement('tr');
          tr.innerHTML = '<td>' + (i+1) + '</td><td>' + p.name + '</td><td>' + p.confidence.toFixed(1) + '%</td><td>' + p.violations + '</td>';
          tbody.appendChild(tr);
        });
      } else {
        tbody.innerHTML = '<tr><td colspan="4" style="text-align:center;color:#8b949e">No data</td></tr>';
      }
    } catch(e) { console.error('Overview error:', e); }
  }

  async function loadPlayers() {
    try {
      const players = await fetchJSON(api('/api/players'));
      const tbody = document.querySelector('#playersTable tbody');
      const search = (document.getElementById('playerSearch').value || '').toLowerCase();
      tbody.innerHTML = '';
      const filtered = players.filter(p => p.name.toLowerCase().includes(search));
      if (!filtered.length) { tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:#8b949e">No players found</td></tr>'; return; }
      filtered.forEach(p => {
        const tr = document.createElement('tr');
        tr.innerHTML = '<td>' + p.name + '</td><td>' + p.confidence.toFixed(1) + '%</td><td>' + p.violations + '</td><td>' + p.ping + 'ms</td><td>' + p.health + '</td>';
        tbody.appendChild(tr);
      });
    } catch(e) { console.error('Players error:', e); }
  }

  document.querySelectorAll('aside li').forEach(li => {
    li.addEventListener('click', function() {
      document.querySelectorAll('aside li, .tab').forEach(el => el.classList.remove('active'));
      this.classList.add('active');
      document.getElementById('tab-' + this.dataset.tab).classList.add('active');
      if (this.dataset.tab === 'overview') loadOverview();
      if (this.dataset.tab === 'players') loadPlayers();
    });
  });

  document.getElementById('refreshBtn').addEventListener('click', loadPlayers);
  document.getElementById('playerSearch').addEventListener('input', loadPlayers);

  loadOverview();
})();
