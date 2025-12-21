package com.embiimob.supmobile;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletTransaction;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

public class SupJSInterface {
    private Context mContext;
    private WebView mWebView;
    private NetworkParameters params;
    private Wallet wallet;
    private PeerGroup peerGroup;
    private BlockStore blockStore;
    private BlockChain blockChain;
    private File walletFile;
    private File chainFile;
    private boolean isMainnet = false;
    private String customStoragePath = null;
    private boolean isRunning = false;

    public SupJSInterface(Context context, WebView webView) {
        mContext = context;
        mWebView = webView;
        // Default to Testnet
        params = TestNet3Params.get();
    }

    // --- Bridge Methods ---

    @JavascriptInterface
    public void startNode() {
        if (isRunning) {
            showToast("Node is already running.");
            return;
        }
        new Thread(() -> {
            try {
                showToast("Starting Bitcoin Node...");
                setupBitcoinJ();
                isRunning = true;
                showToast("Node Started! Peers: 0");
            } catch (Exception e) {
                e.printStackTrace();
                showToast("Error starting node: " + e.getMessage());
            }
        }).start();
    }

    @JavascriptInterface
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
                showToast("Node Stopped.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @JavascriptInterface
    public String getNodeStatus() {
        int peers = (peerGroup != null) ? peerGroup.numConnectedPeers() : 0;
        int height = (blockChain != null) ? blockChain.getBestChainHeight() : 0;
        String path = (chainFile != null) ? chainFile.getAbsolutePath() : "Not initialized";
        return String.format("{\"running\": %b, \"peers\": %d, \"height\": %d, \"path\": \"%s\"}",
                isRunning, peers, height, path);
    }

    @JavascriptInterface
    public void selectStorage() {
        // Trigger generic Storage Access Framework intent in MainActivity
        // For now, we simulate this or rely on manual path setting in the simple version
        showToast("Use 'Set Path' to define storage manually in this version.");
    }

    @JavascriptInterface
    public void setManualStoragePath(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            showToast("Path does not exist: " + path);
            return;
        }
        customStoragePath = path;
        showToast("Storage path set to: " + path);
    }

    @JavascriptInterface
    public void pinIpfs(String hash) {
        new Thread(() -> {
            try {
                // Remove prefix if present
                String cleanHash = hash.replace("ipfs://", "").replace("IPFS:", "");
                URL url = new URL("http://127.0.0.1:5001/api/v0/pin/add?arg=" + cleanHash);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                int code = conn.getResponseCode();
                if (code == 200) {
                    showToast("Pinned to IPFS: " + cleanHash);
                } else {
                    showToast("IPFS Pin Failed: " + code);
                }
            } catch (Exception e) {
                showToast("IPFS Error: " + e.getMessage());
            }
        }).start();
    }

    @JavascriptInterface
    public void watchProfile(String urnOrAddress) {
        if (wallet == null) {
            showToast("Wallet not initialized. Start Node first.");
            return;
        }
        try {
            Address address = Address.fromString(params, urnOrAddress);
            if (wallet.isWatchedScript(ScriptBuilder.createOutputScript(address))) {
                showToast("Already watching " + urnOrAddress);
                return;
            }
            wallet.addWatchedAddress(address);
            wallet.saveToFile(walletFile);
            showToast("Added to watch list: " + urnOrAddress);
        } catch (Exception e) {
            showToast("Invalid Address: " + e.getMessage());
        }
    }

    @JavascriptInterface
    public void mint(String data) {
         showToast("Minting not implemented in this demo (requires funds). Data: " + data);
    }

    // --- Internal Logic ---

    private void setupBitcoinJ() throws Exception {
        // Determine path
        File directory;
        if (customStoragePath != null) {
            directory = new File(customStoragePath);
        } else {
            directory = mContext.getExternalFilesDir(null);
        }

        String filePrefix = isMainnet ? "sup-mainnet" : "sup-testnet";
        walletFile = new File(directory, filePrefix + ".wallet");
        chainFile = new File(directory, filePrefix + ".spvchain");

        // Wallet
        if (walletFile.exists()) {
            wallet = Wallet.loadFromFile(walletFile);
        } else {
            org.bitcoinj.core.Context ctx = org.bitcoinj.core.Context.getOrCreate(params);
            wallet = Wallet.createDeterministic(ctx, Script.ScriptType.P2PKH);
            wallet.saveToFile(walletFile);
        }

        // BlockStore
        blockStore = new SPVBlockStore(params, chainFile);

        // Chain
        blockChain = new BlockChain(params, wallet, blockStore);

        // PeerGroup
        peerGroup = new PeerGroup(params, blockChain);
        peerGroup.addWallet(wallet);

        // Listener for incoming TX
        wallet.addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
            @Override
            public void onCoinsReceived(Wallet w, Transaction tx, Coin prevBalance, Coin newBalance) {
                // Scan for OP_RETURN
                for (TransactionOutput out : tx.getOutputs()) {
                    Script script = out.getScriptPubKey();
                    if (script.isOpReturn()) {
                        String data = new String(script.getChunks().get(1).data); // Simplified
                        if (data.startsWith("IPFS:")) {
                            String hash = data.substring(5);
                            pinIpfs(hash);
                        }
                    }
                }
            }
        });

        peerGroup.setConnectTimeoutMillis(5000);
        peerGroup.start();
        peerGroup.startBlockChainDownload(null);
    }

    private void showToast(String msg) {
        mWebView.post(() -> Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show());
    }
}
