(()=>{'use strict';
if(globalThis.jpGoDds&&globalThis.jpGoDds.behavior)return;
const doc=document;
const FOCUSABLE='a[href],area[href],button:not([disabled]),input:not([disabled]):not([type=hidden]),select:not([disabled]),textarea:not([disabled]),summary,[tabindex]:not([tabindex="-1"]),[contenteditable="true"]';
const visible=el=>!el.closest('[hidden]')&&el.getClientRects().length>0;
const focusables=root=>Array.from(root.querySelectorAll(FOCUSABLE)).filter(visible);
const normalize=s=>String(s||'').normalize('NFKD').replace(/[̀-ͯ]/g,'').toLocaleLowerCase().trim();
let seq=0;const ensureId=(el,prefix)=>{if(!el.id)el.id=prefix+'-'+(++seq);return el.id;};
const reduced=()=>matchMedia('(prefers-reduced-motion: reduce)').matches;
const commandsNative='command' in HTMLButtonElement.prototype;

/* ---- dialog: <dialog data-behavior="dialog"> — showModal() is the focus trap, Escape and focus return are the platform's; this adds the invoker attributes (command/commandfor) where the browser lacks them, data-dialog-open / data-dialog-close, and backdrop-click policy (contracts.edn :settings-sheet :backdrop-click :no-action) */
const dialogOf=(target)=>{const id=target.getAttribute('commandfor')||target.getAttribute('data-dialog-open');return id?doc.getElementById(id):target.closest('dialog');};
const openDialog=(dialog,opener)=>{if(!dialog||dialog.open)return;dialog.__jpOpener=opener||doc.activeElement;dialog.showModal();const first=dialog.querySelector('[autofocus]')||focusables(dialog)[0];if(first&&doc.activeElement!==first)first.focus();};
const closeDialog=dialog=>{if(!dialog||!dialog.open)return;dialog.close();};
doc.addEventListener('click',e=>{
 const t=e.target.closest('[command][commandfor],[data-dialog-open],[data-dialog-close]');if(!t)return;
 if(t.hasAttribute('data-dialog-close')){const d=t.closest('dialog');if(d){e.preventDefault();closeDialog(d);}return;}
 if(t.hasAttribute('data-dialog-open')){e.preventDefault();openDialog(dialogOf(t),t);return;}
 if(commandsNative)return;
 const cmd=t.getAttribute('command');const d=dialogOf(t);if(!d||d.tagName!=='DIALOG')return;
 if(cmd==='show-modal'){e.preventDefault();openDialog(d,t);}else if(cmd==='show'){e.preventDefault();d.show();}else if(cmd==='close'){e.preventDefault();closeDialog(d);}
});
doc.addEventListener('close',e=>{const d=e.target;if(d.tagName!=='DIALOG')return;const o=d.__jpOpener;d.__jpOpener=null;if(o&&o.isConnected&&typeof o.focus==='function'&&!d.contains(o))o.focus();},true);
doc.addEventListener('click',e=>{const d=e.target;if(d.tagName==='DIALOG'&&d.open&&d.dataset.behavior==='dialog'&&d.dataset.backdropClick==='close'){const r=d.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)closeDialog(d);}});

/* ---- menu: [data-behavior="menu"] > [data-menu-opener][aria-expanded] + [data-menu-popup][hidden] (the popup carries data-chrome="float", the layer the audit measures) with [role="menuitem"] items */
const menuParts=root=>({opener:root.querySelector('[data-menu-opener]'),popup:root.querySelector('[data-menu-popup]'),items:Array.from(root.querySelectorAll('[data-menu-popup] [role="menuitem"],[data-menu-popup] [role="menuitemradio"],[data-menu-popup] [role="menuitemcheckbox"],[data-menu-popup] [data-menu-item]')).filter(visible)});
const menus=()=>Array.from(doc.querySelectorAll('[data-behavior="menu"]'));
const closeMenu=(root,refocus)=>{const {opener,popup}=menuParts(root);if(!popup||popup.hidden)return;popup.hidden=true;if(opener)opener.setAttribute('aria-expanded','false');if(refocus&&opener)opener.focus({preventScroll:true});root.dispatchEvent(new CustomEvent('jp-go-dds:menu-close',{bubbles:true}));};
const openMenu=root=>{const {opener,popup,items}=menuParts(root);if(!popup)return;menus().forEach(o=>{if(o!==root&&!o.contains(root))closeMenu(o,false);});popup.hidden=false;if(opener)opener.setAttribute('aria-expanded','true');const first=items.find(i=>i.getAttribute('aria-checked')==='true')||menuParts(root).items[0];if(first)first.focus({preventScroll:true});root.dispatchEvent(new CustomEvent('jp-go-dds:menu-open',{bubbles:true}));};
doc.addEventListener('click',e=>{
 const opener=e.target.closest('[data-menu-opener]');
 if(opener){const root=opener.closest('[data-behavior="menu"]');if(root){e.preventDefault();opener.getAttribute('aria-expanded')==='true'?closeMenu(root,true):openMenu(root);return;}}
 menus().forEach(root=>{if(!root.contains(e.target))closeMenu(root,false);else if(e.target.closest('[data-menu-popup]')&&e.target.closest('[role="menuitem"],[role="menuitemradio"],[role="menuitemcheckbox"],[data-menu-item]')&&!root.hasAttribute('data-menu-keep'))closeMenu(root,true);});
});
doc.addEventListener('focusin',e=>menus().forEach(root=>{if(!root.contains(e.target))closeMenu(root,false);}));
doc.addEventListener('keydown',e=>{
 const root=e.target.closest('[data-behavior="menu"]');if(!root)return;const {opener,popup,items}=menuParts(root);
 if(e.key==='Escape'){if(popup&&!popup.hidden){e.preventDefault();closeMenu(root,true);}return;}
 if(e.target===opener&&(e.key==='ArrowDown'||e.key==='ArrowUp')&&popup&&popup.hidden){e.preventDefault();openMenu(root);return;}
 if(!popup||popup.hidden||!items.length)return;
 if(e.key==='Tab'){closeMenu(root,false);return;}
 const cur=items.indexOf(doc.activeElement);let next=null;
 if(e.key==='ArrowDown')next=(cur+1)%items.length;else if(e.key==='ArrowUp')next=cur<0?items.length-1:(cur-1+items.length)%items.length;else if(e.key==='Home')next=0;else if(e.key==='End')next=items.length-1;
 else if(e.key.length===1&&/\S/.test(e.key)){const k=normalize(e.key);const from=cur+1;for(let i=0;i<items.length;i++){const it=items[(from+i)%items.length];if(normalize(it.textContent).startsWith(k)){next=(from+i)%items.length;break;}}}
 if(next!==null){e.preventDefault();items[next].focus();}
});

/* ---- tabs: [data-behavior="tabs"] with [role="tablist"] > [role="tab"] and [role="tabpanel"] — automatic activation, roving tabindex, arrows by orientation */
const tabParts=root=>{const list=root.querySelector('[role="tablist"]');return {list,tabs:list?Array.from(list.querySelectorAll('[role="tab"]')):[]};};
const panelOf=tab=>{const id=tab.getAttribute('aria-controls')||(tab.getAttribute('href')||'').replace(/^#/,'');return id?doc.getElementById(id):null;};
const selectTab=(root,tab,focus)=>{const {tabs}=tabParts(root);tabs.forEach(t=>{const on=t===tab;t.setAttribute('aria-selected',on?'true':'false');t.setAttribute('tabindex',on?'0':'-1');const p=panelOf(t);if(p){p.hidden=!on;}});if(focus)tab.focus();root.dispatchEvent(new CustomEvent('jp-go-dds:tab-select',{bubbles:true,detail:{id:tab.id,panel:panelOf(tab)}}));};
const initTabs=root=>{const {list,tabs}=tabParts(root);if(!list||!tabs.length)return;tabs.forEach(t=>{const p=panelOf(t);if(p){ensureId(t,'tab');if(!t.hasAttribute('aria-controls'))t.setAttribute('aria-controls',p.id);if(!p.hasAttribute('aria-labelledby'))p.setAttribute('aria-labelledby',t.id);}});const sel=tabs.find(t=>t.getAttribute('aria-selected')==='true')||tabs.find(t=>t.hasAttribute('aria-current'))||tabs[0];selectTab(root,sel,false);};
doc.addEventListener('click',e=>{const tab=e.target.closest('[role="tab"]');const root=tab&&tab.closest('[data-behavior="tabs"]');if(!root)return;if(tab.tagName==='A'&&!root.hasAttribute('data-tabs-navigate'))e.preventDefault();selectTab(root,tab,false);});
doc.addEventListener('keydown',e=>{const tab=e.target.closest('[role="tab"]');const root=tab&&tab.closest('[data-behavior="tabs"]');if(!root)return;const {list,tabs}=tabParts(root);const vertical=list.getAttribute('aria-orientation')==='vertical';const prev=vertical?'ArrowUp':'ArrowLeft',next=vertical?'ArrowDown':'ArrowRight';const i=tabs.indexOf(tab);let n=null;if(e.key===next)n=(i+1)%tabs.length;else if(e.key===prev)n=(i-1+tabs.length)%tabs.length;else if(e.key==='Home')n=0;else if(e.key==='End')n=tabs.length-1;if(n!==null){e.preventDefault();selectTab(root,tabs[n],true);}});

/* ---- disclosure: a control [data-behavior="disclosure"][aria-expanded][aria-controls] that shows or hides its target (a <details> already does this natively; this is for a hamburger opening a menu bar, a "show more") */
doc.addEventListener('click',e=>{const c=e.target.closest('[data-behavior="disclosure"]');if(!c)return;const t=doc.getElementById(c.getAttribute('aria-controls')||'');if(!t)return;e.preventDefault();const open=c.getAttribute('aria-expanded')!=='true';c.setAttribute('aria-expanded',open?'true':'false');t.hidden=!open;if(open){const f=focusables(t)[0];if(f&&c.hasAttribute('data-disclosure-focus'))f.focus();}c.dispatchEvent(new CustomEvent('jp-go-dds:disclosure',{bubbles:true,detail:{open}}));});
doc.addEventListener('keydown',e=>{if(e.key!=='Escape')return;const t=e.target.closest('[id]');const c=t&&doc.querySelector('[data-behavior="disclosure"][aria-expanded="true"][aria-controls="'+t.id+'"]');if(c){c.click();c.focus();}});

/* ---- radiogroup: [data-behavior="radiogroup"] of [role="radio"] controls — arrows move AND choose (click), roving tabindex follows aria-checked; what choosing does stays the host's click handler */
const radios=root=>Array.from(root.querySelectorAll('[role="radio"]')).filter(visible);
const initRadiogroup=root=>{const rs=radios(root);if(!rs.length)return;const on=rs.find(r=>r.getAttribute('aria-checked')==='true')||rs[0];rs.forEach(r=>r.setAttribute('tabindex',r===on?'0':'-1'));};
doc.addEventListener('keydown',e=>{const r=e.target.closest('[role="radio"]');const root=r&&r.closest('[data-behavior="radiogroup"]');if(!root)return;const rs=radios(root);const i=rs.indexOf(r);let n=null;if(e.key==='ArrowRight'||e.key==='ArrowDown')n=(i+1)%rs.length;else if(e.key==='ArrowLeft'||e.key==='ArrowUp')n=(i-1+rs.length)%rs.length;else if(e.key==='Home')n=0;else if(e.key==='End')n=rs.length-1;else if(e.key===' '){e.preventDefault();r.click();return;}if(n===null)return;e.preventDefault();rs[n].focus();rs[n].click();});
doc.addEventListener('click',e=>{const r=e.target.closest('[role="radio"]');const root=r&&r.closest('[data-behavior="radiogroup"]');if(!root)return;radios(root).forEach(x=>x.setAttribute('tabindex',x===r?'0':'-1'));});

/* ---- toast: [data-behavior="toast"] is the live region (role="status" aria-live="polite"); items are .dds-ext-toast, armed with data-timeout (ms, 0 = stays), paused while hovered or focused, dismissed by [data-toast-close] or Escape */
const toastRegion=()=>doc.querySelector('[data-behavior="toast"]');
const dismissToast=item=>{if(!item||item.__jpDone)return;item.__jpDone=true;clearTimeout(item.__jpTimer);const inside=item.contains(doc.activeElement);item.setAttribute('data-leaving','');const gone=()=>{item.remove();if(inside&&item.__jpFrom&&item.__jpFrom.isConnected)item.__jpFrom.focus();item.dispatchEvent(new CustomEvent('jp-go-dds:toast-dismiss',{bubbles:true}));};if(reduced())gone();else setTimeout(gone,200);};
const armToast=item=>{const ms=Number(item.dataset.timeout==null?6000:item.dataset.timeout);if(item.__jpArmed)return;item.__jpArmed=true;item.__jpFrom=doc.activeElement;if(ms>0){const start=()=>{clearTimeout(item.__jpTimer);item.__jpTimer=setTimeout(()=>dismissToast(item),ms);};const stop=()=>clearTimeout(item.__jpTimer);item.addEventListener('mouseenter',stop);item.addEventListener('mouseleave',start);item.addEventListener('focusin',stop);item.addEventListener('focusout',start);start();}};
const toast=(opts)=>{opts=opts||{};const region=opts.region||toastRegion();if(!region)return null;const item=doc.createElement('div');item.className='dds-ext-toast';if(opts.tone)item.dataset.tone=opts.tone;item.dataset.timeout=String(opts.timeout==null?6000:opts.timeout);const text=doc.createElement('p');text.className='dds-ext-toast__text';text.textContent=String(opts.text||'');item.appendChild(text);if(opts.actionLabel){const b=doc.createElement('button');b.type='button';b.className='dds-ext-toast__action';b.textContent=opts.actionLabel;b.addEventListener('click',()=>{if(typeof opts.onAction==='function')opts.onAction();dismissToast(item);});item.appendChild(b);}const c=doc.createElement('button');c.type='button';c.className='dds-ext-toast__close';c.setAttribute('data-toast-close','');c.setAttribute('aria-label',opts.closeLabel||region.dataset.closeLabel||'閉じる');c.textContent='×';item.appendChild(c);region.appendChild(item);armToast(item);return item;};
doc.addEventListener('click',e=>{const c=e.target.closest('[data-toast-close]');if(c)dismissToast(c.closest('.dds-ext-toast'));});
doc.addEventListener('keydown',e=>{if(e.key!=='Escape')return;const item=e.target.closest('.dds-ext-toast');if(item){e.preventDefault();dismissToast(item);}});

/* ---- combobox: [data-behavior="combobox"] > input[role="combobox"][aria-controls] + [role="listbox"][hidden] of [role="option"][data-value] — filter as typed, arrows move aria-activedescendant, Enter chooses, Escape closes; the chosen value lands on the root (data-value), on [data-combobox-value] (a hidden input for the form) and as a change event */
const cbParts=root=>({input:root.querySelector('[role="combobox"]'),list:root.querySelector('[role="listbox"]'),options:Array.from(root.querySelectorAll('[role="option"]')),empty:root.querySelector('[data-combobox-empty]'),value:root.querySelector('[data-combobox-value]')});
const cbVisible=root=>cbParts(root).options.filter(o=>!o.hidden&&!(o.parentElement&&o.parentElement.hidden));
const cbSetActive=(root,opt)=>{const {input,options}=cbParts(root);options.forEach(o=>o.setAttribute('aria-selected',o===opt?'true':'false'));if(opt){ensureId(opt,'option');input.setAttribute('aria-activedescendant',opt.id);if(opt.scrollIntoView)opt.scrollIntoView({block:'nearest'});}else input.removeAttribute('aria-activedescendant');};
const cbOpen=root=>{const {input,list}=cbParts(root);if(!list)return;list.hidden=false;input.setAttribute('aria-expanded','true');};
const cbClose=root=>{const {input,list}=cbParts(root);if(!list||list.hidden)return;list.hidden=true;input.setAttribute('aria-expanded','false');cbSetActive(root,null);};
const cbFilter=root=>{const {input,options,empty}=cbParts(root);const term=normalize(input.value);let n=0;options.forEach(o=>{const hay=normalize(o.dataset.comboboxKeywords||o.textContent);const show=!term||hay.includes(term);const li=o.parentElement&&o.parentElement.tagName==='LI'?o.parentElement:o;li.hidden=!show;if(show)n++;});if(empty)empty.hidden=n>0;return n;};
const cbChoose=(root,opt)=>{const {input,value}=cbParts(root);const label=opt.dataset.label||opt.textContent.trim();const v=opt.dataset.value!=null?opt.dataset.value:label;input.value=label;root.dataset.value=v;if(value)value.value=v;cbClose(root);input.dispatchEvent(new Event('change',{bubbles:true}));root.dispatchEvent(new CustomEvent('jp-go-dds:combobox-choose',{bubbles:true,detail:{value:v,label}}));};
doc.addEventListener('input',e=>{const root=e.target.closest('[data-behavior="combobox"]');if(!root||e.target.getAttribute('role')!=='combobox')return;cbFilter(root);cbOpen(root);cbSetActive(root,null);if(root.dataset.value!=null){delete root.dataset.value;const {value}=cbParts(root);if(value)value.value='';}});
doc.addEventListener('click',e=>{const opt=e.target.closest('[role="option"]');const root=e.target.closest('[data-behavior="combobox"]');if(opt&&root){e.preventDefault();cbChoose(root,opt);return;}const trigger=e.target.closest('[data-combobox-toggle]');if(trigger&&root){const {list,input}=cbParts(root);list&&list.hidden?(cbFilter(root),cbOpen(root),input.focus()):cbClose(root);return;}doc.querySelectorAll('[data-behavior="combobox"]').forEach(r=>{if(!r.contains(e.target))cbClose(r);});});
doc.addEventListener('focusin',e=>doc.querySelectorAll('[data-behavior="combobox"]').forEach(r=>{if(!r.contains(e.target))cbClose(r);}));
doc.addEventListener('keydown',e=>{const root=e.target.closest('[data-behavior="combobox"]');if(!root||e.target.getAttribute('role')!=='combobox')return;const {list}=cbParts(root);const opts=cbVisible(root);const cur=opts.findIndex(o=>o.getAttribute('aria-selected')==='true');
 if(e.key==='ArrowDown'||e.key==='ArrowUp'){e.preventDefault();if(list&&list.hidden){cbFilter(root);cbOpen(root);}const vis=cbVisible(root);if(!vis.length)return;const n=e.key==='ArrowDown'?(cur+1)%vis.length:cur<0?vis.length-1:(cur-1+vis.length)%vis.length;cbSetActive(root,vis[n]);return;}
 if(e.key==='Home'||e.key==='End'){if(list&&!list.hidden&&opts.length){e.preventDefault();cbSetActive(root,opts[e.key==='Home'?0:opts.length-1]);}return;}
 if(e.key==='Enter'){if(list&&!list.hidden&&cur>=0){e.preventDefault();cbChoose(root,opts[cur]);}return;}
 if(e.key==='Escape'){if(list&&!list.hidden){e.preventDefault();cbClose(root);}return;}
 if(e.key==='Tab')cbClose(root);
});

/* ---- init / hydrate */
const hydrate=(root)=>{root=root||doc;root.querySelectorAll('[data-behavior="tabs"]').forEach(initTabs);root.querySelectorAll('[data-behavior="radiogroup"]').forEach(initRadiogroup);root.querySelectorAll('[data-behavior="toast"] .dds-ext-toast').forEach(armToast);if(root!==doc&&root.matches){if(root.matches('[data-behavior="tabs"]'))initTabs(root);if(root.matches('[data-behavior="radiogroup"]'))initRadiogroup(root);}return root;};
const api=Object.freeze({hydrate,toast,openDialog,closeDialog,openMenu,closeMenu,selectTab,version:'0.1.0'});
globalThis.jpGoDds=Object.assign(globalThis.jpGoDds||{},{behavior:api});
if(doc.readyState==='loading')doc.addEventListener('DOMContentLoaded',()=>hydrate());else hydrate();
})();
