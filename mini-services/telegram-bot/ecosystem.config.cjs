module.exports = {
  apps: [{
    name: 'abu-zahra-bot',
    script: 'bot.js',
    cwd: __dirname,
    autorestart: true,
    max_restarts: 9999,
    restart_delay: 5000,
    max_memory_restart: '300M',
    kill_timeout: 5000,
    error_file: './bot-error.log',
    out_file: './bot-out.log',
    time: true,
    merge_logs: true,
    env: {
      NODE_PATH: '/home/z/my-project/node_modules',
      BOT_TOKEN: '8743374928:AAGShUT6RrMfSBQHA6NZsb1nw9xRqA6_9bw',
      ADMIN_CHAT_ID: '7344776596',
      FIREBASE_API_KEY: '91b6cd08b16f5ad4cc62f88674bcff91fb5041e3',
      FIREBASE_DATABASE_URL: 'https://studio-7073076148-6afe0-default-rtdb.firebaseio.com',
      DATABASE_URL: 'file:/home/z/my-project/db/custom.db',
    },
  }],
};
