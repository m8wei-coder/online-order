import { login } from './utils';

test('sends login credentials as form data in the request body', async () => {
  const credentials = { username: 'user+tag@example.test', password: 'p&ss #word' };
  let requestUrl;
  let requestInit;
  global.fetch = jest.fn((url, init) => {
    requestUrl = url;
    requestInit = init;
    return Promise.resolve({ status: 200 });
  });

  await login(credentials);

  expect(requestUrl).toBe('/login');
  expect(requestInit.headers['Content-Type']).toBe('application/x-www-form-urlencoded');
  expect(new URLSearchParams(requestInit.body).get('username')).toBe(credentials.username);
  expect(new URLSearchParams(requestInit.body).get('password')).toBe(credentials.password);
  expect(requestUrl).not.toContain(credentials.password);
});

test('rejects unsuccessful login responses', async () => {
  global.fetch = jest.fn(() => Promise.resolve({ status: 401 }));
  await expect(login({ username: 'user@example.test', password: 'wrong' })).rejects.toThrow('Fail to login');
});
