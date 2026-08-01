// Sync 包入口
const { SyncManager, getDeviceId } = require('./index');
const { SyncServer, SyncClient } = require('./server');
const { LanSyncMdns, getLocalIP } = require('./mdns');
const { buildPayload, applyPayload, PROTOCOL_VERSION, SCHEMA_VERSION } = require('./protocol');
const { encryptPayload, decryptPayload, signRequest, verifyRequest } = require('./crypto');

module.exports = {
  SyncManager, getDeviceId,
  SyncServer, SyncClient,
  LanSyncMdns, getLocalIP,
  buildPayload, applyPayload, PROTOCOL_VERSION, SCHEMA_VERSION,
  encryptPayload, decryptPayload, signRequest, verifyRequest
};
