import test from 'node:test';
import assert from 'node:assert/strict';
import { environment, flush } from './dom.mjs';

function page(path) {
  const number = Number(new URL(path, 'http://test.invalid').searchParams.get('page'));
  return { items: [{ productId: number + 1, title: `제품 ${number + 1}`, modelName: '모델' }], page: { number, size: 20, totalElements: 21, totalPages: 2, hasNext: number === 0 } };
}

test('내 제품은 다음 페이지를 명시적으로 요청하고 전체 개수를 표시한다', async () => {
  const env = environment(path => page(path)); env.window.PastportApi = { fetch: env.window.fetch };
  const home = env.add('section', { id: 'home', className: 'active' });
  for (const name of ['list', 'list-status', 'count', 'purchased-count', 'latest']) env.add('div', { ['data-product-' + name]: '' }, home);
  env.add('div', { 'data-purchased-count': '' }, home);
  env.run('product-registration.js'); env.run('products.js');
  await env.document.emit('pastport:page-shown', { id: 'home' }); await flush();
  assert.equal(home.querySelector('[data-product-count]').textContent, '21');
  await home.querySelector('[data-product-paging]').querySelectorAll('button')[1].emit('click'); await flush();
  assert.ok(env.calls.some(call => call.path === '/api/v1/products?page=1&size=20'));
  assert.equal(home.querySelector('[data-product-paging]').querySelectorAll('button')[1].disabled, true);
  assert.equal(env.calls.length, 2);
});

test('문서 보관함도 모든 제품 페이지에 접근하고 이전 페이지의 제품 문서를 남기지 않는다', async () => {
  const env = environment(path => path.includes('/documents') ? [] : page(path)); env.window.PastportApi = { fetch: env.window.fetch };
  const root = env.add('section', { id: 'documents', className: 'active' });
  for (const name of ['products', 'status', 'title', 'list']) env.add('div', { ['data-docs-' + name]: '' }, root);
  env.run('documents.js'); await env.document.emit('pastport:page-shown', { id: 'documents' }); await flush();
  await root.querySelector('[data-docs-products]').querySelectorAll('button').at(-1).emit('click'); await flush();
  assert.match(root.querySelector('[data-docs-products]').textContent, /제품 2/);
  assert.ok(env.calls.some(call => call.path === '/api/v1/products/2/documents'));
  assert.equal(env.calls.filter(call => call.path.startsWith('/api/v1/products?')).length, 2);
});

test('마스킹 제품 선택기는 페이지를 미리 전부 읽지 않고 다음 제품으로 이동한다', async () => {
  const env = environment(path => page(path)); env.window.PastportApi = { fetch: env.window.fetch };
  env.window.location.search = '';
  const root = env.add('section', { id: 'docmask' }); env.add('div', { 'data-dm': '' }, root);
  for (const name of ['overlay', 'file', 'undo', 'clear', 'auto', 'cancel', 'preview', 'back', 'confirm', 'save', 'again', 'count']) env.add('div', { ['data-dm-' + name]: '' }, root);
  const select = env.add('select', { 'data-dm-product': '' }, root); select.value = ''; select.add = option => select.append(option);
  const Option = function(label, value) { const option = env.document.createElement('option'); option.textContent = label; option.value = value; return option; };
  env.run('doc-mask.js', { Option, Headers }); await flush();
  assert.equal(env.calls.length, 1);
  await root.querySelector('[data-dm-product-paging]').querySelectorAll('button')[1].emit('click'); await flush();
  assert.match(select.textContent, /제품 2/);
  assert.equal(env.calls.length, 2);
});
