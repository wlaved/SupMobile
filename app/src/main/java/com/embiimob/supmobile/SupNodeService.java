package com.embiimob.supmobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletTransaction;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SupNodeService extends Service {
    private static final String CHANNEL_ID = "SupNodeChannel";
    private static final int NOTIFICATION_ID = 1;
    private final IBinder binder = new LocalBinder();

    // Core Bitcoin State
    private NetworkParameters params;
    private Wallet wallet;
    private PeerGroup peerGroup;
    private BlockStore blockStore;
    private BlockChain blockChain;
    private boolean isRunning = false;
    private boolean isMainnet = false;
    private String customStoragePath = null;

    // Listeners
    private List<NodeEventListener> listeners = new ArrayList<>();

    // Database
    private SupDatabaseHelper dbHelper;

    public interface NodeEventListener {
        void onEvent(String type, String data);
    }

    public class LocalBinder extends Binder {
        SupNodeService getService() {
            return SupNodeService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        dbHelper = new SupDatabaseHelper(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if ("START".equals(action)) {
                startForeground(NOTIFICATION_ID, createNotification("Sup Node Active"));

                // Check Auto-Scan Pref
                android.content.SharedPreferences prefs = getSharedPreferences("SupPrefs", Context.MODE_PRIVATE);
                boolean autoScan = prefs.getBoolean("autoScan", false);
                if (autoScan) {
                    // Slight delay to ensure config is set if coming from fresh start
                    // But config usually comes from bind.
                    // For now, if autoScan is set, we assume path is set too in prefs and handled by setupBitcoinJ or manual trigger.
                    // Actually, setupBitcoinJ handles config internally if we move config loading here or verify it.
                    // Let's just log it for now, as startLocalScan relies on 'params' which needs 'isMainnet'.
                    // We need to load prefs here to support full auto-start independent of UI binding.
                    this.customStoragePath = prefs.getString("storagePath", null);
                    this.isMainnet = prefs.getBoolean("isMainnet", false);
                    this.params = isMainnet ? MainNetParams.get() : TestNet3Params.get();

                    startLocalScan();
                }

            } else if ("STOP".equals(action)) {
                stopNode();
                stopForeground(true);
                stopSelf();
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // --- Public API for Clients ---

    public void registerListener(NodeEventListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void unregisterListener(NodeEventListener listener) {
        listeners.remove(listener);
    }

    public void setConfiguration(boolean isMainnet, String path) {
        this.isMainnet = isMainnet;
        this.customStoragePath = path;
        this.params = isMainnet ? MainNetParams.get() : TestNet3Params.get();
    }

    public void startNode() {
        if (isRunning) {
            broadcast("system_log", "Node already running.");
            return;
        }

        // Ensure foreground
        Intent intent = new Intent(this, SupNodeService.class);
        intent.setAction("START");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        new Thread(() -> {
            try {
                broadcast("system_log", "Starting Bitcoin Node (" + (isMainnet ? "Mainnet" : "Testnet") + ")...");
                setupBitcoinJ();
                isRunning = true;
                broadcast("system_log", "Node Started! Waiting for peers...");
            } catch (Exception e) {
                e.printStackTrace();
                broadcast("system_log", "Error starting node: " + e.getMessage());
                stopForeground(true);
            }
        }).start();
    }

    public void stopNode() {
        if (!isRunning) return;
        new Thread(() -> {
            try {
                if (peerGroup != null) {
                    peerGroup.stop();
                    peerGroup = null;
                }
                if (blockStore != null) {
                    blockStore.close();
                    blockStore = null;
                }
                isRunning = false;
                broadcast("system_log", "Node Stopped.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public String getStatusJson() {
        int peers = (peerGroup != null) ? peerGroup.numConnectedPeers() : 0;
        int height = (blockChain != null) ? blockChain.getBestChainHeight() : 0;
        // Use either the custom path or internal files dir if not set
        String path = (customStoragePath != null) ? customStoragePath : getExternalFilesDir(null).getAbsolutePath();
        return String.format("{\"running\": %b, \"peers\": %d, \"height\": %d, \"path\": \"%s\"}",
                isRunning, peers, height, path.replace("\\", "/"));
    }

    public void startLocalScan() {
        new Thread(() -> {
            try {
                broadcast("system_log", "Starting Local Blockchain Scan...");

                File rootDir;
                if (customStoragePath != null) {
                    File customRoot = new File(customStoragePath);
                    if (isMainnet) {
                        rootDir = new File(customRoot, "bitcoin");
                    } else {
                        rootDir = new File(new File(customRoot, "bitcoin"), "testnet3");
                    }
                } else {
                    rootDir = getExternalFilesDir(null);
                }

                File blocksDir = new File(rootDir, "blocks");
                File scanTarget = blocksDir.exists() ? blocksDir : rootDir;

                boolean hasBlocks = false;
                if (scanTarget.exists() && scanTarget.isDirectory()) {
                    File[] check = scanTarget.listFiles((d, name) -> name.startsWith("blk") && name.endsWith(".dat"));
                    hasBlocks = check != null && check.length > 0;
                }

                if (!hasBlocks) {
                    broadcast("system_log", "No blk*.dat files found in: " + scanTarget.getAbsolutePath());
                    return;
                }

                BlockchainScanner scanner = new BlockchainScanner(params, scanTarget.getAbsolutePath());
                scanner.scanForOpReturn(new BlockchainScanner.OpReturnListener() {
                    @Override
                    public void onOpReturnFound(String txId, byte[] data) {
                        String asciiData = new String(data);
                        // In Scanner, sender is hard to know without full index, assume generic for now
                        // or try to parse if Sup protocol puts address in OP_RETURN (it usually doesn't, it's in inputs)
                        // For now, mark isWatched=false for history scan unless we match content?
                        // Or just let history be global.
                        boolean isWatched = false;

                        String json = String.format("{\"type\":\"scanner\", \"sender\":\"History\", \"content\":\"%s\", \"txid\":\"%s\", \"watched\":%b}",
                                            asciiData.replace("\"", "\\\"").replace("\n", " "), txId, isWatched);
                        broadcast("new_post", json);

                        // Index Locally
                        dbHelper.addMessage(txId, "History", asciiData);

                        if (asciiData.startsWith("IPFS")) {
                             broadcast("ipfs_found", asciiData.substring(5));
                             broadcast("system_log", "Scanner Found IPFS: " + asciiData);
                        }
                    }

                    @Override
                    public void onProgress(int blocksScanned) {
                        broadcast("system_log", "Scanning... Processed " + blocksScanned + " blocks");
                    }

                    @Override
                    public void onScanComplete() {
                        broadcast("system_log", "Blockchain Scan Complete!");
                    }

                    @Override
                    public void onScanError(String error) {
                        broadcast("system_log", "Scan Error: " + error);
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                broadcast("system_log", "Scanner Failed: " + e.getMessage());
            }
        }).start();
    }

    // --- Internal Logic ---

    private void setupBitcoinJ() throws Exception {
        File directory;
        if (customStoragePath != null) {
            File root = new File(customStoragePath);
            if (isMainnet) {
                directory = new File(root, "bitcoin");
            } else {
                directory = new File(new File(root, "bitcoin"), "testnet3");
            }
            if (!directory.exists()) {
                directory.mkdirs();
            }
        } else {
            directory = getExternalFilesDir(null);
        }

        String filePrefix = isMainnet ? "sup-mainnet" : "sup-testnet";
        File walletFile = new File(directory, filePrefix + ".wallet");
        File chainFile = new File(directory, filePrefix + ".spvchain");

        if (walletFile.exists()) {
            wallet = Wallet.loadFromFile(walletFile);
        } else {
            org.bitcoinj.core.Context ctx = org.bitcoinj.core.Context.getOrCreate(params);
            wallet = Wallet.createDeterministic(ctx, Script.ScriptType.P2PKH);
            wallet.saveToFile(walletFile);
        }

        blockStore = new SPVBlockStore(params, chainFile);
        blockChain = new BlockChain(params, wallet, blockStore);
        peerGroup = new PeerGroup(params, blockChain);
        peerGroup.addWallet(wallet);
        peerGroup.addPeerDiscovery(new DnsDiscovery(params));

        wallet.addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
            @Override
            public void onCoinsReceived(Wallet w, Transaction tx, Coin prevBalance, Coin newBalance) {
                String txId = tx.getTxId().toString();
                // Check if relevant to wallet (Watched)
                // In bitcoinj, onCoinsReceived fires for relevant transactions.
                // If it's in the wallet, it's either ours or watched.
                boolean isWatched = true; // By definition of this event firing for SPV wallet

                for (TransactionOutput out : tx.getOutputs()) {
                    Script script = out.getScriptPubKey();
                    if (script.isOpReturn()) {
                        try {
                            byte[] dataBytes = null;
                            if (script.getChunks().size() > 1) {
                                dataBytes = script.getChunks().get(1).data;
                            }
                            if (dataBytes != null) {
                                String data = new String(dataBytes);
                                String sender = "Watched/Mempool";
                                String json = String.format("{\"type\":\"message\", \"sender\":\"%s\", \"content\":\"%s\", \"txid\":\"%s\", \"watched\":%b}",
                                    sender, data.replace("\"", "\\\"").replace("\n", " "), txId, isWatched);
                                broadcast("new_post", json);

                                // Index Locally
                                dbHelper.addMessage(txId, sender, data);

                                if (data.startsWith("IPFS:")) {
                                    String hash = data.substring(5);
                                    broadcast("ipfs_found", hash);
                                    broadcast("system_log", "Auto-Pinning IPFS: " + hash);
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });

        peerGroup.setConnectTimeoutMillis(5000);
        peerGroup.start();
        peerGroup.startBlockChainDownload(null);
    }

    private void broadcast(String type, String data) {
        for (NodeEventListener listener : listeners) {
            listener.onEvent(type, data);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Sup Node Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private Notification createNotification(String contentText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Sup Node")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .build();
    }
}
