// ---------- Screen registry ----------
  const NAV = {home:'Home',block:'Block',focus:'Focus',schedule:'Schedule',profile:'Profile'};
  const SCREENS = {
    welcome:{group:'Onboarding'}, permissions:{group:'Onboarding'}, permbattery:{group:'Onboarding'}, permalarms:{group:'Onboarding'}, perma11y:{group:'Onboarding'},
    home:{group:'Main',nav:true}, block:{group:'Main',nav:true}, focus:{group:'Main',nav:true},
    schedule:{group:'Main',nav:true}, profile:{group:'Main',nav:true},
    keywords:{group:'Blocking'}, appfeature:{group:'Blocking'},
    safebrowse:{group:'Blocking'}, blockscreen:{group:'Blocking'}, vpn:{group:'Blocking'}, antitamper:{group:'Blocking'}, selfprotect:{group:'Blocking'}, titleblock:{group:'Blocking'},
    applock:{group:'Protection'}, accountability:{group:'Protection'}, backup:{group:'Protection'},
    crash:{group:'Protection'}, troubleshoot:{group:'Protection'}, about:{group:'Protection'}, protected:{group:'Protection'}, relay:{group:'Protection'},
    history:{group:'Focus & Schedules'}, focusactive:{group:'Focus & Schedules'}, focuswhitelist:{group:'Focus & Schedules'}, scheduleedit:{group:'Focus & Schedules'}
  };
  const ORDER = ['welcome','permissions','permbattery','permalarms','perma11y','home','block','focus','schedule','profile','keywords','appfeature','safebrowse','blockscreen','vpn','antitamper','selfprotect','titleblock','applock','accountability','backup','crash','troubleshoot','about','protected','relay','history','focusactive','focuswhitelist','scheduleedit'];
  let stack = [];

  // Build prototype navigator
  const groups = {};
  ORDER.forEach((s,i)=>{ (groups[SCREENS[s].group] = groups[SCREENS[s].group]||[]).push([s,i]); });
  const ptb = document.getElementById('ptbGroups');
  for(const g in groups){
    ptb.insertAdjacentHTML('beforeend',`<div class="ptb-grp">${g}</div>`);
    groups[g].forEach(([s,i])=>{
      ptb.insertAdjacentHTML('beforeend',`<a href="#/s/${s}" data-pt="${s}" class="${s==='home'?'on':''}">${title(s)}<span class="d">${String(i+1).padStart(2,'0')}</span></a>`);
    });
  }

  function title(s){ return s.replace(/([A-Z])/g,' $1').replace(/^./,c=>c.toUpperCase()); }

  function show(name, push){
    document.querySelectorAll('.screen').forEach(el=>el.classList.remove('show'));
    document.getElementById('sc-'+name).classList.add('show');
    document.getElementById('sc-'+name).scrollTop = 0;
    const cfg = SCREENS[name]||{};
    const navEl = document.getElementById('nav');
    navEl.classList.toggle('show', !!cfg.nav);
    document.querySelectorAll('#nav button').forEach(b=>b.classList.toggle('on', b.dataset.nav===name));
    document.querySelectorAll('#ptbGroups a').forEach(a=>a.classList.toggle('on', a.dataset.pt===name));
    document.getElementById('statusbar').style.visibility = 'visible';
    if(push) stack.push(name);
  }

  function nav(name){ show(name, true); location.hash = '#/s/'+name; }
  function back(){
    if(stack.length>1){ stack.pop(); const prev = stack[stack.length-1]; show(prev,false); location.hash='#/s/'+prev; }
    else nav('home');
  }
  const wasOnboarded = localStorage.getItem('safeme_onboarded');
  function permBack(name){ if(wasOnboarded) back(); else nav(name); }
  function permAdvance(btn){
    const nx = btn.dataset.next;
    if(nx) nav(nx);
    else finishOnboard();
  }
  function grantPerm(btn){
    const card = btn.closest('[data-perm]');
    if(!card) return;
    btn.textContent='Granted ✓';
    btn.classList.remove('btn-primary','btn-secondary');
    btn.classList.add('btn-secondary');
    btn.disabled = true;
    toast('Permission granted ✓');
    permStatus();
    permAdvance(btn);
  }
  function skipPerm(btn){
    toast('Skipped — you can grant it later from Profile');
    permAdvance(btn);
  }
  function finishOnboard(){
    const required=[...document.querySelectorAll('[data-perm][data-required]')];
    const missing = required.filter(c=>!c.querySelector('[data-grant]').disabled);
    if(missing.length){ toast('Grant the required permissions to continue'); return; }
    localStorage.setItem('safeme_onboarded','1');
    nav('home');
    toast(wasOnboarded ? 'Permissions updated — you’re protected' : 'Welcome to SafeMe — you’re protected');
  }
  function permStatus(){
    const cards=[...document.querySelectorAll('[data-perm]')];
    const total=cards.length;
    const granted=cards.filter(c=>c.querySelector('[data-grant]').disabled).length;
    const card=document.getElementById('permCardSub');
    if(card) card.textContent=(total && granted>=total)?'All permissions granted ✓':(granted+' of '+(total||4)+' granted · finish setup');
  }

  // hash router
  window.addEventListener('hashchange', ()=>{
    const m = location.hash.match(/#\/s\/(\w+)/);
    const name = m ? m[1] : (localStorage.getItem('safeme_onboarded') ? 'home' : 'welcome');
    if(document.getElementById('sc-'+name)){ show(name, stack[stack.length-1]!==name); }
  });

  // ---------- Generic toggle ----------
  document.addEventListener('click', e=>{
    const t = e.target.closest('.sw[data-toggle]');
    if(t){ t.classList.toggle('on'); toast(t.classList.contains('on')?'On':'Off'); if(t.closest('#schedList')) schedCount(); if(t.closest('#titleList')) titleCount(); if(t.id==='vpnToggle') vpnStatus(); }
  });

  // ---------- In-screen tabs ----------
  document.querySelectorAll('.tabs[data-tabs]').forEach(tb=>{
    tb.querySelectorAll('button').forEach(btn=>{
      btn.addEventListener('click', ()=>{
        tb.querySelectorAll('button').forEach(b=>b.classList.remove('on'));
        btn.classList.add('on');
        tb.parentElement.querySelectorAll('[data-panel-view]').forEach(p=>p.style.display='none');
        const panel = tb.parentElement.querySelector('[data-panel-view="'+btn.dataset.panel+'"]');
        if(panel) panel.style.display='block';
      });
    });
  });

  // ---------- Keyword chips / filter ----------
  let kwCat='all', kwQuery='', kwEditId=null, siteCat='all', siteQuery='', siteEditId=null;
  function applyKwFilter(){
    document.querySelectorAll('#kwAllList .li').forEach(r=>{
      const t = r.querySelector('.t');
      const catOk = kwCat==='all' || r.dataset.cat===kwCat;
      const qOk = !kwQuery || (t && t.textContent.includes(kwQuery));
      r.style.display = (catOk && qOk) ? '' : 'none';
    });
  }
  document.querySelectorAll('#kwAllChips .chip').forEach(c=>{
    c.addEventListener('click', ()=>{
      document.querySelectorAll('#kwAllChips .chip').forEach(x=>x.classList.remove('on'));
      c.classList.add('on');
      kwCat = c.dataset.cat;
      applyKwFilter();
    });
  });
  function filterKwAll(v){ kwQuery=v; applyKwFilter(); }
  function applySiteFilter(){
    document.querySelectorAll('#siteAllList .li').forEach(r=>{
      const t = r.querySelector('.t');
      const catOk = siteCat==='all' || r.dataset.cat===siteCat;
      const qOk = !siteQuery || (t && t.textContent.toLowerCase().includes(siteQuery));
      r.style.display = (catOk && qOk) ? '' : 'none';
    });
  }
  function filterSites(v){ siteQuery=v.trim().toLowerCase(); applySiteFilter(); }
  document.querySelectorAll('#siteChips .chip').forEach(c=>{
    c.addEventListener('click', ()=>{
      document.querySelectorAll('#siteChips .chip').forEach(x=>x.classList.remove('on'));
      c.classList.add('on');
      siteCat = c.dataset.cat;
      applySiteFilter();
    });
  });
  function openManage(which){
    kwCat='all'; kwQuery=''; kwEditId=null;
    document.querySelectorAll('#kwAllChips .chip').forEach(x=>x.classList.remove('on'));
    const allChip = document.querySelector('#kwAllChips .chip[data-cat="all"]');
    if(allChip) allChip.classList.add('on');
    applyKwFilter();
    siteCat='all'; siteQuery=''; siteEditId=null;
    document.querySelectorAll('#siteChips .chip').forEach(x=>x.classList.remove('on'));
    const siteAll = document.querySelector('#siteChips .chip[data-cat="all"]');
    if(siteAll) siteAll.classList.add('on');
    applySiteFilter();
    const btn = document.querySelector('#sheetKwAll .tabs.u button[data-panel="'+(which==='site'?'mg-site':'mg-kw')+'"]');
    if(btn) btn.click();
    document.getElementById('scrim').classList.add('show');
    document.getElementById('sheetKwAll').classList.add('show');
  }
  function updateMgCard(){
    const nk = document.querySelectorAll('#kwAllList .li').length;
    const ns = document.querySelectorAll('#siteAllList .li').length;
    const nw = document.querySelectorAll('#wlAllList .li').length;
    const s = document.getElementById('mgCardSub');
    if(s) s.textContent = nk+' keywords · '+ns+' websites · '+nw+' trusted';
  }
  function removeRow(btn){
    const li = btn.closest('.li');
    if(!li) return;
    li.remove();
    updateMgCard();
    toast('Removed');
  }
  updateMgCard();

  // ---------- Sheets ----------
  let delayTimer=null;
  function openSheet(id){
    document.getElementById('scrim').classList.add('show');
    document.getElementById(id).classList.add('show');
    if(id==='sheetDelay'){ startDelay(); }
    if(id==='sheetTitle'){
      titleEditId=null; titleApp='';
      const tt=document.getElementById('titleSheetTitle'); if(tt) tt.textContent='Add title rule';
      const sb=document.getElementById('titleSaveBtn'); if(sb) sb.textContent='Add Title';
      const dl=document.getElementById('titleDel'); if(dl) dl.style.display='none';
      const inp=document.getElementById('titleInput'); if(inp) inp.value='';
      document.querySelectorAll('#sheetTitle #titleMode button').forEach((b,i)=>b.classList.toggle('on',i===0));
      document.querySelectorAll('#sheetTitle #titleScope button').forEach((b,i)=>b.classList.toggle('on',i===0));
      const an=document.getElementById('titleAppName'); if(an) an.textContent='Choose an app';
      const aw=document.getElementById('titleAppWrap'); if(aw) aw.style.display='none';
    }
    if(id==='sheetKw'){
      kwEditId=null;
      const kt=document.getElementById('kwSheetTitle'); if(kt) kt.textContent='Add keyword';
      const kb=document.getElementById('kwSaveBtn'); if(kb) kb.textContent='Add';
      const ki=document.getElementById('kwInput'); if(ki) ki.value='';
      document.querySelectorAll('#kwCat .chip').forEach((b,i)=>b.classList.toggle('on',i===0));
    }
    if(id==='sheetSite'){
      siteEditId=null;
      const st=document.getElementById('siteSheetTitle'); if(st) st.textContent='Add website';
      const sb=document.getElementById('siteSaveBtn'); if(sb) sb.textContent='Add';
      const si=document.getElementById('siteInput'); if(si) si.value='';
      document.querySelectorAll('#siteCat .chip').forEach((b,i)=>b.classList.toggle('on',i===0));
    }
    if(id==='sheetDnsCustom'){
      const v4=document.getElementById('dnsIpv4'), v6=document.getElementById('dnsIpv6');
      if(v4) v4.value=dnsV4; if(v6) v6.value=dnsV6;
    }
    if(id==='sheetBsImg'){
      const cur=document.querySelector('#sheetBsImg .img-tile[data-img="'+bsImg+'"]')||document.querySelector('#sheetBsImg .img-tile.clear');
      document.querySelectorAll('#sheetBsImg .img-tile').forEach(t=>t.classList.remove('on'));
      if(cur) cur.classList.add('on');
    }
    if(id==='sheetBsMsg'){
      const mi=document.getElementById('bsMsgInput'); if(mi) mi.value=bsMsg;
      document.querySelectorAll('#bsMsgChips .chip').forEach((b,i)=>b.classList.toggle('on',i===0));
    }
    if(id==='sheetBsUrl'){
      const ui=document.getElementById('bsUrlInput'); if(ui) ui.value=bsRedirect;
    }
  }
  function closeSheets(){
    document.getElementById('scrim').classList.remove('show');
    document.querySelectorAll('.sheet').forEach(s=>s.classList.remove('show'));
    if(delayTimer){ clearInterval(delayTimer); delayTimer=null; }
  }
  function addKeyword(){
    const v = document.getElementById('kwInput').value.trim();
    if(!v){ toast('Keyword can’t be empty'); return; }
    const cat = document.querySelector('#kwCat .chip.on').textContent;
    if(kwEditId && kwEditId.isConnected){
      const row = kwEditId;
      row.dataset.cat=cat;
      row.querySelector('.t').textContent=v;
      const s=row.querySelector('.s');
      const m = s && /(\d+) hits today/.exec(s.textContent);
      if(s) s.textContent=cat+' · '+(m?m[1]:'0')+' hits today';
      kwEditId=null;
      closeSheets(); applyKwFilter(); updateMgCard(); toast('Keyword updated');
      return;
    }
    const row = document.createElement('div');
    row.className='li'; row.dataset.cat=cat;
    row.innerHTML = `<div class="ic r"><svg viewBox="0 0 24 24"><path d="M4 9h16M4 15h16M10 3L8 21M16 3l-2 18"/></svg></div><div class="tx"><div class="t">${v}</div><div class="s">${cat} · 0 hits today</div></div><button class="act" onclick="openKwEdit(this)"><svg viewBox="0 0 24 24"><path d="M17 3a2.8 2.8 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5z"/></svg></button><button class="act" style="color:var(--danger)" onclick="removeRow(this)"><svg viewBox="0 0 24 24"><path d="M4 7h16M9 7V5h6v2M7 7l1 13h8l1-13"/></svg></button>`;
    const list = document.getElementById('kwAllList');
    list.insertBefore(row, list.firstChild);
    closeSheets(); applyKwFilter(); updateMgCard(); toast('Keyword added to '+cat);
  }
  function openKwEdit(li){
    if(!li) return;
    openSheet('sheetKw');
    kwEditId = li;
    const kt=document.getElementById('kwSheetTitle'); if(kt) kt.textContent='Edit keyword';
    const kb=document.getElementById('kwSaveBtn'); if(kb) kb.textContent='Save';
    const ki=document.getElementById('kwInput'); if(ki) ki.value=(li.querySelector('.t')||{textContent:''}).textContent;
    const cat = li.dataset.cat || 'Custom';
    document.querySelectorAll('#kwCat .chip').forEach(b=>b.classList.toggle('on',b.textContent===cat));
  }
  function addSite(){
    const si = document.getElementById('siteInput');
    let v = (si.value||'').trim().replace(/^https?:\/\//i,'').replace(/\/+$/,'').toLowerCase();
    if(!v){ toast('Website can’t be empty'); return; }
    if(/\s/.test(v) || v.indexOf('.')===-1){ toast('Enter a valid domain (e.g. example.com)'); return; }
    const cat = document.querySelector('#siteCat .chip.on').textContent;
    const sameEdit = siteEditId && siteEditId.isConnected && siteEditId.querySelector('.t').textContent===v;
    if(!sameEdit){
      const dup = [...document.querySelectorAll('#siteAllList .t')].some(t=>t.textContent===v);
      if(dup){ toast('Already on the list'); return; }
    }
    if(siteEditId && siteEditId.isConnected){
      const row = siteEditId;
      row.dataset.cat=cat;
      row.querySelector('.t').textContent=v;
      row.querySelector('.s').textContent=cat+' · blocked';
      siteEditId=null;
      closeSheets(); applySiteFilter(); updateMgCard(); toast('Website updated');
      return;
    }
    const row = document.createElement('div');
    row.className='li'; row.dataset.cat=cat;
    row.innerHTML = `<div class="ic d"><svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 3a14 14 0 000 18M12 3a14 14 0 010 18M3 12h18"/></svg></div><div class="tx"><div class="t">${v}</div><div class="s">${cat} · blocked</div></div><button class="act" onclick="openSiteEdit(this)"><svg viewBox="0 0 24 24"><path d="M17 3a2.8 2.8 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5z"/></svg></button><button class="act" style="color:var(--danger)" onclick="removeRow(this)"><svg viewBox="0 0 24 24"><path d="M4 7h16M9 7V5h6v2M7 7l1 13h8l1-13"/></svg></button>`;
    const list = document.getElementById('siteAllList');
    list.insertBefore(row, list.firstChild);
    closeSheets(); applySiteFilter(); updateMgCard(); toast('Website added');
  }
  function openSiteEdit(li){
    if(!li) return;
    openSheet('sheetSite');
    siteEditId = li;
    const st=document.getElementById('siteSheetTitle'); if(st) st.textContent='Edit website';
    const sb=document.getElementById('siteSaveBtn'); if(sb) sb.textContent='Save';
    const si=document.getElementById('siteInput'); if(si) si.value=(li.querySelector('.t')||{textContent:''}).textContent;
    const cat = li.dataset.cat || 'Custom';
    document.querySelectorAll('#siteCat .chip').forEach(b=>b.classList.toggle('on',b.textContent===cat));
  }
  function togSel(li){ const c=li.querySelector('.checkbox'); c.classList.toggle('on'); }

  // ---------- Time delay gate ----------
  function testDelay(){
    openSheet('sheetDelay');
  }
  function startDelay(){
    let n=12; const num=document.getElementById('delayNum'); const fill=document.getElementById('delayFill');
    fill.style.width='100%';
    clearInterval(delayTimer);
    delayTimer = setInterval(()=>{
      n--; if(n<=0){ clearInterval(delayTimer); delayTimer=null; num.textContent='0'; fill.style.width='0%'; toast('Change applied'); closeSheets(); return; }
      num.textContent=n; fill.style.width=(n/12*100)+'%';
    }, 1000);
  }
  function cancelDelay(){ if(delayTimer){clearInterval(delayTimer); delayTimer=null;} closeSheets(); toast('Change cancelled'); }

  // ---------- Block overlay ----------
  let bsDwell=5, bsMsg='Stay safe, Alex. You chose this.', bsImg='', bsRedirect='';
  function renderBlockPreview(){
    const msg=document.getElementById('bsPreviewMsg'); if(msg) msg.textContent=bsMsg;
    const cnt=document.getElementById('bsPreviewCount'); if(cnt) cnt.textContent='Closing in '+bsDwell+'s…';
    const dv=document.getElementById('bsDwellVal'); if(dv) dv.textContent=bsDwell+'s';
    const is=document.getElementById('bsImgSub'); if(is) is.textContent=bsImg?(bsImg+' · persistable'):'None · SAF persistable URI';
    const pi=document.getElementById('bsPreviewImg'); if(pi){ if(bsImg){ pi.style.display='block'; pi.className='img-'+bsImg; } else { pi.style.display='none'; pi.className=''; } }
    const ms=document.getElementById('bsMsgSub'); if(ms) ms.textContent='"'+bsMsg+'"';
    const rs=document.getElementById('bsRedirectSub'); if(rs) rs.textContent=bsRedirect||'None — returns to the app';
    const bm=document.getElementById('boMsg'); if(bm) bm.textContent=bsMsg;
    const why=document.getElementById('boWhy'); const sw=document.getElementById('bsWhySw'); if(why&&sw) why.style.display=sw.classList.contains('on')?'':'none';
  }
  function dwellStep(d){
    bsDwell=Math.max(3,Math.min(120,bsDwell+d));
    renderBlockPreview();
  }
  function pickImg(btn){
    document.querySelectorAll('#sheetBsImg .img-tile').forEach(t=>t.classList.remove('on'));
    btn.classList.add('on');
  }
  function saveImg(){
    const sel=document.querySelector('#sheetBsImg .img-tile.on'); if(sel) bsImg=sel.dataset.img||'';
    renderBlockPreview(); closeSheets(); toast(bsImg?('Motivation image set to '+bsImg):'No motivation image');
  }
  function pickMsgChip(chip){
    document.querySelectorAll('#bsMsgChips .chip').forEach(c=>c.classList.remove('on'));
    chip.classList.add('on');
    const inp=document.getElementById('bsMsgInput'); if(inp) inp.value=chip.textContent;
  }
  function saveMsg(){
    const v=(document.getElementById('bsMsgInput').value||'').trim();
    if(!v){ toast('Message can’t be empty'); return; }
    bsMsg=v; renderBlockPreview(); closeSheets(); toast('Message saved');
  }
  function saveUrl(){
    const v=(document.getElementById('bsUrlInput').value||'').trim();
    if(v && !/^https?:\/\/.+/i.test(v)){ toast('Enter a valid URL (https://…)'); return; }
    bsRedirect=v; renderBlockPreview(); closeSheets(); toast(v?'Redirect set to '+v:'Redirect cleared');
  }
  function clearRedirect(){
    bsRedirect=''; renderBlockPreview(); toast('Redirect cleared');
  }
  function openBlockov(){
    renderBlockPreview();
    document.getElementById('blockov').classList.add('show');
    let n=bsDwell; const num=document.getElementById('boNum'); const close=document.getElementById('boClose');
    close.classList.remove('ready'); close.classList.add('locked'); num.textContent=n;
    const t = setInterval(()=>{
      n--; num.textContent=Math.max(n,0);
      if(n<=0){ clearInterval(t); close.classList.add('ready'); close.classList.remove('locked'); }
    },1000);
  }
  function closeBlockov(){ document.getElementById('blockov').classList.remove('show'); toast(bsRedirect?('Redirecting to '+bsRedirect):'Back to the app'); }
  renderBlockPreview();

  // ---------- Presets / pickers ----------
  function pickPreset(el){ document.querySelectorAll('.preset').forEach(p=>p.classList.remove('on')); el.classList.add('on'); }
  function pickDns(el){
    const l=el.closest('.list'); l.querySelectorAll('.checkbox').forEach(c=>c.classList.remove('on')); el.querySelector('.checkbox').classList.add('on');
    vpnPreset=el.querySelector('.t').textContent;
    if(vpnPreset==='Custom preset'){
      const v4=document.getElementById('dnsIpv4'), v6=document.getElementById('dnsIpv6');
      if(v4) v4.value=dnsV4; if(v6) v6.value=dnsV6;
      openSheet('sheetDnsCustom');
    } else { toast('Preset: '+vpnPreset); }
    vpnStatus();
  }
  function pickAcc(el){ const l=el.closest('.list'); l.querySelectorAll('.checkbox').forEach(c=>c.classList.remove('on')); el.querySelector('.checkbox').classList.add('on'); toast('Accountability type set'); }
  // ---------- App Lock ----------
  let lockState='off', lockMethod='pin', lockCode='', lockAuto='Immediately';
  let wizMethod='pin', wizPin='', wizValPin='', patOrder=[], patOrderV=[];
  let unlockVal='', unlockPat=[];
  function methodLabel(){ return {pin:'PIN',password:'Password',pattern:'Pattern'}[lockMethod]; }
  function renderLock(){
    const hero=document.getElementById('lockHero'); if(!hero) return;
    hero.classList.toggle('lock-on', lockState==='on'); hero.classList.toggle('lock-off', lockState!=='on');
    document.getElementById('lockHeroTitle').textContent = lockState==='on' ? 'Locked with '+methodLabel() : 'App Lock is off';
    document.getElementById('lockHeroSub').textContent = lockState==='on' ? 'SafeMe locks automatically when you leave the app.' : 'Lock SafeMe with a PIN, password or pattern before anyone else opens it.';
    const pill=document.getElementById('lockHeroPill'); pill.className='pill '+(lockState==='on'?'g':'r'); pill.innerHTML='<span class="pdot"></span>'+(lockState==='on'?'On':'Off');
    const autoMap={Immediately:'Immediately after lock','After 15 seconds':'After 15 seconds idle','After 30 seconds':'After 30 seconds idle','After 1 minute':'After 1 minute idle','After 5 minutes':'After 5 minutes idle',Off:'Never auto-lock'};
    const av=document.getElementById('autoVal'); if(av) av.textContent=autoMap[lockAuto]||lockAuto;
  }
  function openLockSetup(){
    wizMethod=lockMethod; wizPin=''; wizValPin=''; patOrder=[]; patOrderV=[];
    const pc=document.getElementById('pcInput'); if(pc) pc.value='';
    const pv=document.getElementById('pvInput'); if(pv) pv.value='';
    document.querySelectorAll('#sheetLockSetup .patgrid button').forEach(b=>{ b.classList.remove('on'); b.textContent=''; });
    const ok=document.getElementById('setupOk'); if(ok) ok.classList.remove('show');
    showSetStep(1);
    document.getElementById('scrim').classList.add('show');
    document.getElementById('sheetLockSetup').classList.add('show');
  }
  function setLockMethod(m){
    wizMethod=m;
    document.querySelectorAll('#setM .li').forEach(li=>li.querySelector('.checkbox').classList.toggle('on', li.dataset.m===m));
    const nb=document.getElementById('setupNext'); if(nb) nb.disabled=false;
  }
  function showSetStep(n){
    ['setM','setC','setV'].forEach((id,i)=>{ const el=document.getElementById(id); if(el) el.style.display=(i+1===n)?'block':'none'; });
    document.querySelectorAll('#sheetLockSetup .steps span').forEach((s,i)=>s.classList.toggle('on', i<n));
    const t=document.getElementById('setupTitle'), sub=document.getElementById('setupSub');
    if(n===1){ t.textContent='Choose lock method'; sub.textContent='How do you want to lock SafeMe?'; }
    else if(n===2){ t.textContent='Create your lock'; sub.textContent='Enter a code to lock SafeMe'; }
    else { t.textContent='Confirm your lock'; sub.textContent='Enter it again to confirm'; }
    if(n===1) document.querySelectorAll('#setM .li').forEach(li=>li.querySelector('.checkbox').classList.toggle('on', li.dataset.m===wizMethod));
    document.querySelectorAll('.set-body').forEach(body=>{ body.querySelectorAll('.set-ui').forEach(ui=>{ ui.style.display=ui.dataset.m===wizMethod?'block':'none'; }); });
    const ok1=document.getElementById('setCNext'), ok2=document.getElementById('setVDone'), nb=document.getElementById('setupNext');
    if(nb) nb.disabled=true;
    if(ok1) ok1.disabled=!wizInputValid('c');
    if(ok2) ok2.disabled=!wizInputValid('v');
  }
  function setupStep(n){ if(n===2) resetWiz('c'); if(n===3) resetWiz('v'); showSetStep(n); }
  function resetWiz(stage){
    if(stage==='c'){ wizPin=''; patOrder=[]; const pc=document.getElementById('pcInput'); if(pc) pc.value=''; }
    else { wizValPin=''; patOrderV=[]; const pv=document.getElementById('pvInput'); if(pv) pv.value=''; }
    document.querySelectorAll(stage==='c'?'#patGridC button':'#patGridV button').forEach(b=>{ b.classList.remove('on'); b.textContent=''; });
    renderDotsId(stage==='c'?'pcDots':'pvDots', stage==='c'?wizPin:wizValPin);
  }
  function getWizCode(stage){
    if(wizMethod==='pin') return stage==='c'?wizPin:wizValPin;
    if(wizMethod==='password'){ const el=document.getElementById(stage==='c'?'pcInput':'pvInput'); return el?el.value:''; }
    return stage==='c'?patOrder.join('-'):patOrderV.join('-');
  }
  function wizInputValid(stage){ const v=getWizCode(stage); if(wizMethod==='pin'||wizMethod==='password') return v.length>=4; return v.split('-').length>=4; }
  function refreshWizBtn(stage){ const ok=wizInputValid(stage); const btn=document.getElementById(stage==='c'?'setCNext':'setVDone'); if(btn) btn.disabled=!ok; }
  function setPin(d,stage){ const max=(stage==='c'?wizPin:wizValPin).length>=6; if(max) return; if(stage==='c') wizPin+=d; else wizValPin+=d; renderDotsId(stage==='c'?'pcDots':'pvDots', stage==='c'?wizPin:wizValPin); refreshWizBtn(stage); }
  function pinBackS(stage){ if(stage==='c') wizPin=wizPin.slice(0,-1); else wizValPin=wizValPin.slice(0,-1); renderDotsId(stage==='c'?'pcDots':'pvDots', stage==='c'?wizPin:wizValPin); refreshWizBtn(stage); }
  function wizInput(stage){ refreshWizBtn(stage); }
  function patTap(btn,stage){ const arr=stage==='c'?patOrder:patOrderV; const i=btn.dataset.i; if(arr.includes(i)) return; arr.push(i); btn.classList.add('on'); btn.textContent=arr.length; refreshWizBtn(stage); }
  function renderDotsId(elId,val){ const host=document.getElementById(elId); if(!host) return; host.querySelectorAll('.pindot').forEach((d,i)=>d.classList.toggle('f', i<val.length)); }
  function saveLock(){
    const c=getWizCode('c'), v=getWizCode('v');
    if(!wizInputValid('v') || c!==v){
      const card=document.getElementById('setV'); card.classList.remove('shake'); void card.offsetWidth; card.classList.add('shake');
      resetWiz('v'); toast('Codes don\'t match — try again'); return;
    }
    lockState='on'; lockMethod=wizMethod; lockCode=c;
    const ok=document.getElementById('setupOk'); ok.classList.add('show');
    setTimeout(()=>{ ok.classList.remove('show'); closeSheets(); renderLock(); toast('App Lock is on'); }, 700);
  }
  function pickAuto(el,label){ el.closest('.list').querySelectorAll('.checkbox').forEach(c=>c.classList.remove('on')); el.querySelector('.checkbox').classList.add('on'); lockAuto=label; renderLock(); toast('Auto-lock: '+label); closeSheets(); }
  function lockNow(){ unlockVal=''; unlockPat=[]; renderUnlock(); document.getElementById('lockov').classList.add('show'); }
  function closeLockov(){ document.getElementById('lockov').classList.remove('show'); }
  function renderUnlock(){
    const b=document.getElementById('unlockBody');
    document.getElementById('lockovSub').textContent='Enter your '+methodLabel()+' to unlock';
    if(lockMethod==='pin'){
      b.innerHTML='<div class="pindots" id="unlockDots">'+'<span class="pindot"></span>'.repeat(lockCode.length)+'</div><div class="keypad">'+
        ['1','2','3','4','5','6','7','8','9'].map(n=>'<button onclick="unlockPin(\''+n+'\')">'+n+'</button>').join('')+
        '<button class="sym" style="visibility:hidden"></button><button onclick="unlockPin(\'0\')">0</button><button class="sym" onclick="unlockBack()">⌫</button></div>';
    }
    else if(lockMethod==='password'){
      b.innerHTML='<div class="field" style="margin-top:16px"><svg viewBox="0 0 24 24"><path d="M12 3a4 4 0 00-4 4v3H6a1 1 0 00-1 1v9a1 1 0 001 1h12a1 1 0 001-1v-9a1 1 0 00-1-1h-2V7a4 4 0 00-4-4z"/></svg><input id="unlockPass" type="password" placeholder="Enter password" oninput="unlockPass(this.value)"></div>';
    }
    else {
      b.innerHTML='<div class="patgrid" id="unlockPat">'+[1,2,3,4,5,6,7,8,9].map(i=>'<button data-i="'+i+'" onclick="patUnlock(this)"></button>').join('')+'</div><div class="s-sub" style="text-align:center;margin-top:12px">Draw your pattern</div>';
    }
    document.getElementById('lockErr').textContent='';
  }
  function unlockPin(d){ if(unlockVal.length>=lockCode.length) return; unlockVal+=d; renderDotsId('unlockDots',unlockVal); if(unlockVal.length===lockCode.length) checkUnlock(); }
  function unlockBack(){ unlockVal=unlockVal.slice(0,-1); renderDotsId('unlockDots',unlockVal); }
  function unlockPass(v){ unlockVal=v; if(v.length>=lockCode.length) checkUnlock(); }
  function patUnlock(btn){ const i=btn.dataset.i; if(unlockPat.includes(i)) return; unlockPat.push(i); btn.classList.add('on'); btn.textContent=unlockPat.length; if(unlockPat.length>=lockCode.split('-').length){ unlockVal=unlockPat.join('-'); checkUnlock(); } }
  function checkUnlock(){
    if(unlockVal===lockCode){ closeLockov(); toast('Unlocked'); renderLock(); }
    else {
      unlockVal=''; unlockPat=[];
      const card=document.getElementById('lockovCard'); card.classList.remove('shake'); void card.offsetWidth; card.classList.add('shake');
      renderUnlock(); document.getElementById('lockErr').textContent='Wrong code — try again';
    }
  }
  renderLock();

  // ---------- Theme ----------
  let themePref = (function(){ try{ return localStorage.getItem('safeme_theme') || 'System'; }catch(e){ return 'System'; } })();
  const mqDark = window.matchMedia('(prefers-color-scheme: dark)');
  function applyTheme(){
    const dark = themePref==='Dark' || (themePref==='System' && mqDark.matches);
    document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
  }
  function setTheme(btn,label){
    btn.parentElement.querySelectorAll('button').forEach(b=>b.classList.remove('on'));
    btn.classList.add('on');
    themePref = label;
    try{ localStorage.setItem('safeme_theme', label); }catch(e){}
    applyTheme();
    toast(label==='System'?'Following system theme':label+' theme applied');
  }
  function initTheme(){
    document.querySelectorAll('#themeSeg button').forEach(b=>b.classList.toggle('on', b.textContent.trim()===themePref));
    applyTheme();
  }
  try{ mqDark.addEventListener('change', applyTheme); }catch(e){ mqDark.addListener(applyTheme); }
  initTheme();

  // ---------- Focus ----------
  function startFocus(){
    nav('focusactive'); toast('Focus started — 25:00 countdown');
  }
  let schedEditId=null, timeTarget='start', timeH=21, timeM=0;
  function h12(h){ return (h%12)||12; }
  function fmt12h(h24){ const p=h24.split(':'); const h=+p[0], m=p[1]; return h12(h)+'<span class="sep">:</span>'+m+'<span class="ampm"> '+(h<12?'AM':'PM')+'</span>'; }
  function setTimep(el,h24){ el.dataset.h24=h24; el.innerHTML=fmt12h(h24); }
  function saveSchedule(){
    const name=document.getElementById('schedName');
    if(!name||!name.value.trim()){ toast('Give your schedule a name'); return; }
    const days=[...document.querySelectorAll('#sc-scheduleedit .day')].map((d,i)=>d.classList.contains('on')?i:-1).filter(i=>i>=0);
    if(!days.length){ toast('Pick at least one day'); return; }
    const st=document.getElementById('timeStart'), en=document.getElementById('timeEnd');
    const start=(st&&st.dataset.h24)||'21:00', end=(en&&en.dataset.h24)||'23:00';
    if(start>=end){ toast('Start must be before end'); return; }
    const mode=(document.querySelector('#sc-scheduleedit .seg button.on')||{textContent:'Both'}).textContent;
    const apps=(parseInt((document.getElementById('schedAppCount')||{}).textContent||'12',10)||0);
    const dayNames=['Mon','Tue','Wed','Thu','Fri','Sat','Sun'];
    const o={name:name.value.trim(),days:daysLabel(days),daysraw:days.map(i=>dayNames[i]).join(','),start:start,end:end,mode:mode,modetxt:modeTxt(mode),apps:apps};
    const html=schedCardHTML(o);
    if(schedEditId){ schedEditId.outerHTML=html; schedEditId=null; }
    else { document.getElementById('schedList').insertAdjacentHTML('beforeend',html); }
    schedCount(); nav('schedule'); toast('Schedule saved — alarms set');
  }
  function schedCardHTML(o){
    return '<div class="sched-card" data-name="'+o.name+'" data-days="'+o.days+'" data-daysraw="'+o.daysraw+'" data-start="'+o.start+'" data-end="'+o.end+'" data-mode="'+o.mode+'" data-modetxt="'+o.modetxt+'" data-apps="'+o.apps+'">'+
      '<div class="sched-head">'+
        '<div class="ic o"><svg viewBox="0 0 24 24"><rect x="4" y="5" width="16" height="16" rx="3"/><path d="M4 10h16M9 3v4M15 3v4"/></svg></div>'+
        '<div class="tx"><div class="t">'+o.name+'</div><div class="s">'+o.days+'</div></div>'+
        '<div class="sw on" data-toggle></div>'+
      '</div>'+
      '<div class="sched-time"><svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg><span>'+o.start+' – '+o.end+'</span></div>'+
      '<div class="sched-pills"><span class="pill g">'+o.modetxt+'</span><span class="pill o">'+o.apps+' apps</span></div>'+
      '<div class="sched-foot"><span>Next · '+o.days+'</span><button class="btn btn-ghost sm" onclick="openSchedEdit(this)">Edit</button></div>'+
    '</div>';
  }
  function daysLabel(arr){ const full=['Mon','Tue','Wed','Thu','Fri','Sat','Sun']; return arr.length===7?'Daily':arr.map(i=>full[i]).join(' · '); }
  function modeTxt(m){ return m==='Both'?'Internet + Launch':m==='Internet'?'Internet blocked':'Launch blocked'; }
  function openSchedEdit(btn){
    const title=document.getElementById('schedEditTitle'), save=document.getElementById('schedSaveBtn'), del=document.getElementById('schedDel');
    const name=document.getElementById('schedName');
    const days=[...document.querySelectorAll('#sc-scheduleedit .day')];
    const dayNames=['Mon','Tue','Wed','Thu','Fri','Sat','Sun'];
    const ts=document.getElementById('timeStart'), te=document.getElementById('timeEnd');
    const seg=[...document.querySelectorAll('#sc-scheduleedit .seg button')];
    const apc=document.getElementById('schedAppCount'), aps=document.getElementById('schedAppSub');
    if(!btn){
      schedEditId=null;
      if(del) del.style.display='none';
      if(title) title.textContent='New Schedule';
      if(save) save.textContent='Create schedule';
      if(name) name.value='';
      days.forEach((d,i)=>d.classList.toggle('on',i<3));
      setTimep(ts,'21:00'); setTimep(te,'23:00');
      seg.forEach(b=>b.classList.toggle('on',b.textContent==='Both'));
      if(apc) apc.textContent='12 apps selected';
      if(aps) aps.textContent='TikTok, Instagram, YouTube, Reddit…';
      nav('scheduleedit'); return;
    }
    const c=btn.closest('.sched-card'); schedEditId=c;
    if(del) del.style.display='block';
    if(title) title.textContent='Edit Schedule';
    if(save) save.textContent='Save changes';
    if(name) name.value=c.dataset.name;
    const raw=(c.dataset.daysraw||'').split(',');
    days.forEach((d,i)=>d.classList.toggle('on',raw.includes(dayNames[i])));
    setTimep(ts,c.dataset.start||'21:00'); setTimep(te,c.dataset.end||'23:00');
    seg.forEach(b=>b.classList.toggle('on',b.textContent===c.dataset.mode));
    if(apc) apc.textContent=(c.dataset.apps||'12')+' apps selected';
    if(aps) aps.textContent='Tap Choose to change apps';
    nav('scheduleedit');
  }
  function togDay(btn){
    btn.classList.toggle('on');
    if(!document.querySelectorAll('#sc-scheduleedit .day.on').length){ btn.classList.add('on'); toast('Pick at least one day'); }
  }
  function togSeg(btn){ btn.parentElement.querySelectorAll('button').forEach(b=>b.classList.remove('on')); btn.classList.add('on'); }
  function openTime(t){
    timeTarget=t;
    const el=document.getElementById(t==='start'?'timeStart':'timeEnd');
    const m=((el&&el.dataset.h24)||(el?el.textContent:'21:00')).split(':');
    timeH=+m[0]; timeM=+(m[1]||'0');
    renderTime();
    document.getElementById('scrim').classList.add('show');
    document.getElementById('sheetTime').classList.add('show');
  }
  function renderTime(){
    document.getElementById('timeH').textContent=String(h12(timeH));
    document.getElementById('timeM').textContent=String(timeM).padStart(2,'0');
    document.querySelectorAll('#sheetTime .seg button').forEach(b=>b.classList.toggle('on',b.textContent.trim()===(timeH<12?'AM':'PM')));
  }
  function timeStep(u,d){
    if(u==='h'){ timeH=Math.min(23,Math.max(0,timeH+d)); }
    else { timeM=Math.min(59,Math.max(0,timeM+d)); }
    renderTime();
  }
  function togAmpm(btn){
    if(btn.textContent.trim()==='AM') timeH=timeH%12;
    else timeH=(timeH%12)+12;
    renderTime();
  }
  function timeDone(){
    const v=String(timeH).padStart(2,'0')+':'+String(timeM).padStart(2,'0');
    const el=document.getElementById(timeTarget==='start'?'timeStart':'timeEnd');
    setTimep(el,v);
    const a=(document.getElementById('timeStart').dataset.h24)||'21:00', b=(document.getElementById('timeEnd').dataset.h24)||'23:00';
    if(a>=b){ toast('Start must be before end'); return; }
    closeSheets();
  }
  function delSchedule(){
    if(!schedEditId) return;
    schedEditId.remove(); schedEditId=null;
    schedCount(); nav('schedule'); toast('Schedule deleted');
  }
  function schedCount(){
    const all=document.querySelectorAll('#schedList .sched-card');
    const on=[...all].filter(c=>c.querySelector('.sw').classList.contains('on')).length;
    const c=document.getElementById('schedHeroCount'); if(c) c.textContent=on+' active schedule'+(on===1?'':'s');
    const p=document.getElementById('schedHeroPill'); if(p){ p.className='pill '+(on?'g':'r'); p.innerHTML='<span class="pdot"></span>'+(on?'On':'Off'); }
    const sub=document.getElementById('schedHeroSub');
    if(sub){
      const first=on?[...all].find(c=>c.querySelector('.sw').classList.contains('on')):null;
      sub.textContent=first?('Next boundary · '+(first.dataset.name||'Schedule')+' at '+(first.dataset.start||'--:--')):(on===0&&all.length?'All schedules paused':'No schedules yet — create one');
    }
  }
  schedCount();

  // ---------- Title Block ----------
  let titleEditId=null, titleApp='';
  function titleSearch(){
    const q=(document.getElementById('titleSearch').value||'').trim().toLowerCase();
    let shown=0;
    document.querySelectorAll('#titleList .li').forEach(r=>{
      const hit=!q||(r.querySelector('.t').textContent+' '+(r.querySelector('.s').textContent||'')).toLowerCase().includes(q);
      r.style.display=hit?'':'none';
      if(hit) shown++;
    });
    const nm=document.getElementById('titleNoMatch'); if(nm) nm.style.display=(q&&!shown)?'':'none';
  }
  function addTitle(){
    const inp=document.getElementById('titleInput');
    const v=(inp&&inp.value.trim())||'';
    if(!v){ toast('Enter a title to block'); return; }
    const mode=(document.querySelector('#sheetTitle #titleMode button.on')||{textContent:'Contains'}).textContent;
    const scope=(document.querySelector('#sheetTitle #titleScope button.on')||{textContent:'All Apps'}).textContent;
    if(scope==='Specific App'&&!titleApp){ toast('Choose an app to target'); return; }
    const dup=[...document.querySelectorAll('#titleList .li')].some(r=>r!==titleEditId&&r.querySelector('.t').textContent.toLowerCase()===v.toLowerCase());
    if(dup){ toast('Rule already exists'); return; }
    const row=document.createElement('div');
    row.className='li';
    row.innerHTML='<div class="ic d"><svg viewBox="0 0 24 24"><path d="M4 6h16M4 12h16M4 18h16"/></svg></div><div class="tx"><div class="t"></div><div class="s"></div></div><div class="sw on" data-toggle></div><button class="act" style="color:var(--danger)" onclick="editTitle(this)"><svg viewBox="0 0 24 24"><path d="M4 7h16M9 7V5h6v2M7 7l1 13h8l1-13"/></svg></button>';
    row.querySelector('.t').textContent=v;
    row.querySelector('.s').textContent=mode+' · '+scope+(scope==='Specific App'&&titleApp?' · '+titleApp:'');
    if(titleEditId){ titleEditId.replaceWith(row); titleEditId=null; }
    else { document.getElementById('titleList').appendChild(row); }
    titleCount(); titleSearch(); closeSheets(); toast('Rule added');
  }
  function editTitle(btn){
    const li=btn.closest('.li');
    titleEditId=li;
    const v=li.querySelector('.t').textContent;
    const parts=(li.querySelector('.s').textContent||'').split(' · ');
    const mode=parts[0]||'Contains', scope=parts[1]||'All Apps', app=parts.slice(2).join(' · ');
    titleApp=app;
    const inp=document.getElementById('titleInput'); if(inp) inp.value=v;
    document.querySelectorAll('#sheetTitle #titleMode button').forEach(b=>b.classList.toggle('on',b.textContent===mode));
    document.querySelectorAll('#sheetTitle #titleScope button').forEach(b=>b.classList.toggle('on',b.textContent===scope));
    titleScopeChg();
    const an=document.getElementById('titleAppName'); if(an) an.textContent=app||'Choose an app';
    const tt=document.getElementById('titleSheetTitle'); if(tt) tt.textContent='Edit title rule';
    const sb=document.getElementById('titleSaveBtn'); if(sb) sb.textContent='Save changes';
    const dl=document.getElementById('titleDel'); if(dl) dl.style.display='';
    document.getElementById('scrim').classList.add('show');
    document.getElementById('sheetTitle').classList.add('show');
  }
  function delTitle(){
    if(!titleEditId) return;
    titleEditId.remove(); titleEditId=null;
    closeSheets(); titleCount(); toast('Rule deleted');
  }
  function titleCount(){
    const all=document.querySelectorAll('#titleList .li');
    const on=[...all].filter(r=>r.querySelector('.sw').classList.contains('on')).length;
    const h=document.getElementById('titleHeroTitle'); if(h) h.textContent=all.length?(on?'Title blocking is on':'Title blocking is paused'):'No title rules yet';
    const tag=document.querySelector('#titleHero .tag'); if(tag) tag.textContent=all.length?(on?'Active':'Paused'):'Setup';
    const sub=document.getElementById('titleHeroSub');
    if(sub) sub.textContent=all.length?(all.length+' rule'+(all.length===1?'':'s')+' · '+on+' active · matching in real time'):'Add a rule to start blocking by window title';
    const empty=document.getElementById('titleEmpty'); if(empty) empty.style.display=all.length?'none':'';
  }
  titleCount();
  (function(){
    const inp=document.getElementById('titleInput');
    if(inp) inp.addEventListener('keydown',e=>{ if(e.key==='Enter') addTitle(); });
  })();
  function titleScopeChg(){
    const scope=(document.querySelector('#sheetTitle #titleScope button.on')||{textContent:'All Apps'}).textContent;
    const aw=document.getElementById('titleAppWrap'); if(aw) aw.style.display=scope==='Specific App'?'':'none';
  }
  function pickTitleApp(btn){
    titleApp=btn.querySelector('.t').textContent;
    document.querySelectorAll('#sheetTitleApps .li .checkbox').forEach(c=>c.classList.remove('on'));
    const cb=btn.querySelector('.checkbox'); if(cb) cb.classList.add('on');
    const an=document.getElementById('titleAppName'); if(an) an.textContent=titleApp;
    closeSheets(); toast('App selected: '+titleApp);
  }
  function filterTitleApps(q){
    const v=(q||'').trim().toLowerCase();
    document.querySelectorAll('#sheetTitleApps .li').forEach(r=>{
      r.style.display=!v||(r.querySelector('.t').textContent.toLowerCase().includes(v))?'':'none';
    });
  }

  function appsDone(){
    const rows=[...document.querySelectorAll('#sheetApps .li')];
    const picked=rows.filter(r=>r.querySelector('.checkbox').classList.contains('on'));
    const names=picked.map(r=>r.querySelector('.t').textContent);
    const editor=document.getElementById('sc-scheduleedit');
    if(editor && editor.classList.contains('show')){
      const apc=document.getElementById('schedAppCount'), aps=document.getElementById('schedAppSub');
      if(apc) apc.textContent=names.length+' app'+(names.length===1?'':'s')+' selected';
      if(aps) aps.textContent=names.length?(names.slice(0,4).join(', ')+(names.length>4?'…':'')):'No apps — schedule blocks everything';
    }
    closeSheets(); toast(names.length+' app'+(names.length===1?'':'s')+' selected');
  }
  function filterApps(q){
    const v=(q||'').trim().toLowerCase();
    document.querySelectorAll('#sheetApps .li').forEach(r=>{
      r.style.display=r.querySelector('.t').textContent.toLowerCase().includes(v)?'':'none';
    });
  }

  // ---------- DNS & VPN ----------
  let vpnPreset='Cloudflare Family', dnsV4='1.1.1.1', dnsV6='', vpnWhitelist=[], vpnNotif='Default';
  function saveDns(){
    const v4=document.getElementById('dnsIpv4').value.trim();
    const v6=document.getElementById('dnsIpv6').value.trim();
    if(!/^(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}$/.test(v4)){ toast('Enter a valid IPv4 address'); return; }
    if(v6 && !/^([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}$/.test(v6)){ toast('Enter a valid IPv6 address'); return; }
    dnsV4=v4; dnsV6=v6;
    const cs=document.getElementById('dnsCustomSub'); if(cs) cs.textContent='IPv4 '+v4+(v6?' · IPv6 '+v6:'');
    vpnStatus(); closeSheets(); toast('Custom DNS saved');
  }
  function vpnAppsDone(){
    const names=[...document.querySelectorAll('#vpnAppList .li .checkbox.on')].map(c=>c.closest('.li').querySelector('.t').textContent);
    vpnWhitelist=names;
    const sub=document.getElementById('vpnWhitelistSub'); if(sub) sub.textContent=names.length?names.length+' app'+(names.length===1?'':'s')+' exempt':'No apps exempt';
    closeSheets(); toast(names.length?names.length+' app'+(names.length===1?'':'s')+' whitelisted':'Whitelist cleared'); vpnStatus();
  }
  function filterVpnApps(q){
    const v=(q||'').trim().toLowerCase();
    document.querySelectorAll('#vpnAppList .li').forEach(r=>{ r.style.display=!v||r.querySelector('.t').textContent.toLowerCase().includes(v)?'':'none'; });
  }
  function setVpnNotif(btn){
    vpnNotif=btn.textContent;
    const w=document.getElementById('vpnNotifWrap'); if(w) w.style.display=vpnNotif==='Custom'?'':'none';
    toast('Notification: '+vpnNotif);
  }
  function vpnStatus(){
    const on=document.getElementById('vpnToggle').classList.contains('on');
    const p=document.getElementById('vpnPill'); if(p){ p.className='pill '+(on?'g':'r'); p.innerHTML='<span class="pdot"></span>'+(on?'Active':'Off'); }
    const tt=document.getElementById('vpnTitle'); if(tt) tt.textContent=on?'VPN filtering is on':'VPN filtering is off';
    const s=document.getElementById('vpnSub');
    if(s){
      const base=vpnPreset==='Custom preset'?('Custom · '+dnsV4+(dnsV6?' · '+dnsV6:'')):vpnPreset;
      s.textContent=on?(base+' · '+vpnWhitelist.length+' exempt'):'Tap to re-enable protection';
    }
  }
  vpnStatus();

  // ---------- Accessibility status ----------
  let a11yOn=false;
  function openA11y(){
    toast('Opening: Accessibility → SafeMe…');
    setTimeout(()=>{ a11yOn=true; a11yStatus(); toast('Accessibility service enabled'); }, 1600);
  }
  function a11yStatus(){
    const b=document.getElementById('a11yBanner');
    const on=a11yOn;
    if(b){
      b.style.transition='max-height .3s ease, opacity .3s ease, padding .3s ease, margin .3s ease, border-width .3s ease';
      b.style.overflow='hidden';
      if(on){
        b.style.maxHeight='0'; b.style.opacity='0'; b.style.paddingTop='0'; b.style.paddingBottom='0'; b.style.borderWidth='0'; b.style.marginTop='0'; b.style.pointerEvents='none';
        b.style.background='rgba(34,181,115,.07)'; b.style.borderColor='var(--success)';
      } else {
        b.style.maxHeight='90px'; b.style.opacity='1'; b.style.paddingTop=''; b.style.paddingBottom=''; b.style.borderWidth=''; b.style.marginTop='12px'; b.style.pointerEvents='';
        b.style.background='rgba(244,67,54,.07)'; b.style.borderColor='var(--danger)';
      }
    }
    const ic=document.getElementById('a11yIc'); if(ic) ic.className='ic '+(on?'g':'r');
    const p=document.getElementById('a11yPill'); if(p){ p.className='pill '+(on?'g':'r'); p.innerHTML='<span class="pdot"></span>'+(on?'Enabled':'Disabled'); }
    const tt=document.getElementById('a11yTitle'); if(tt) tt.textContent=on?'Accessibility service is on':'Accessibility service is off';
    const s=document.getElementById('a11ySub'); if(s) s.textContent=on?'Core blocking engine active':"SafeMe can't detect blocked content until you enable it";
  }

  // ---------- Toast ----------
  function toast(msg){
    const box=document.getElementById('toasts');
    const t=document.createElement('div'); t.className='toast'; t.textContent=msg;
    box.appendChild(t);
    requestAnimationFrame(()=>t.classList.add('show'));
    setTimeout(()=>{ t.classList.remove('show'); setTimeout(()=>t.remove(),300); }, 2400);
  }

  // ---------- Boot ----------
  (function boot(){
    const onboarded = localStorage.getItem('safeme_onboarded');
    const m = location.hash.match(/#\/s\/(\w+)/);
    const name = (m && document.getElementById('sc-'+m[1])) ? m[1] : null;
    const obScreens = ['welcome','permissions','permbattery','permalarms','perma11y'];
    if(!onboarded){ show(name || 'welcome'); }
    else if(name && !obScreens.includes(name)){ nav(name); }
    else { show('home'); }
    // permissions init
    permStatus();
  })();
