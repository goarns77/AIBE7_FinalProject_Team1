const pageNames={home:'내 제품',passport:'제품 여권',register:'새 제품 등록',documents:'문서 보관함',ai:'AI 이력 검색',market:'중고 거래',trade:'거래 · 소유권 이전'};
const show=(name)=>{if(document.activeElement instanceof HTMLElement)document.activeElement.blur();const target=(name==='documents'||name==='ai')?'passport':name;document.querySelectorAll('.page').forEach(page=>page.classList.toggle('active',page.id===target));document.querySelectorAll('[data-page]').forEach(button=>button.classList.toggle('active',button.dataset.page===name));document.querySelector('#crumb').textContent=pageNames[name];document.querySelector('#side-crumb').textContent=pageNames[name];document.querySelector('#side').classList.remove('open');window.scrollTo(0,0)};
pageNames.landing='홈';
document.addEventListener('click',(event)=>{const target=event.target.closest('[data-page]');if(target)show(target.dataset.page)});
document.querySelector('#menu').addEventListener('click',()=>document.querySelector('#side').classList.toggle('open'));
