package com.smartargs.mempolis.deed;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Contract;
import io.neow3j.devpack.ECPoint;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Helper;
import io.neow3j.devpack.Iterator;
import io.neow3j.devpack.List;
import io.neow3j.devpack.Map;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.annotations.ContractSourceCode;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.EventParameterNames;
import io.neow3j.devpack.annotations.ManifestExtra;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.OnNEP17Payment;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.annotations.SupportedStandard;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.constants.FindOptions;
import io.neow3j.devpack.constants.NamedCurveHash;
import io.neow3j.devpack.constants.NativeContract;
import io.neow3j.devpack.constants.NeoStandard;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.contracts.CryptoLib;
import io.neow3j.devpack.contracts.GasToken;
import io.neow3j.devpack.contracts.StdLib;
import io.neow3j.devpack.events.Event1Arg;
import io.neow3j.devpack.events.Event2Args;
import io.neow3j.devpack.events.Event4Args;

/**
 * The Deeds of one Mempolis city: one soulbound NEP-11 token per wallet, standing for its home on a lot of the city's
 * plan. The plan's lots are registered append-only, each with a tier and a ring, so a registered lot never changes.
 * <p>
 * A wallet claims in one of three ways. A <b>free claim</b> takes the next lot of the published queue of standard lots
 * and the city's free house style; it needs a voucher from the gate key, unless the wallet is a founder in the
 * city's guestbook. A <b>newcomer claim</b> gets the same lot by paying the newcomer fee in GAS. A <b>chosen claim</b>
 * pays its lot's tier price in GAS and picks a choice or prime lot inside the open rings and any house style. VIP lots
 * are reserved. Every payment is forwarded to the treasury at once, so the contract holds no funds.
 * <p>
 * The owner, set at deploy, registers lots and the queue, sets prices, opens rings and phases, replaces the gate key
 * and the treasury and updates the code. The guestbook is deploy data, so the manifest may call {@code numberOf} and
 * {@code isOpen} on any contract; only the stored guestbook is ever called. The gate key can only approve wallets for a free claim; it cannot touch a deed, a lot or the
 * contract's state. Nobody can move, take back or transfer a deed.
 */
@DisplayName("MempolisDeed")
@SupportedStandard(neoStandard = NeoStandard.NEP_11)
@ContractSourceCode("https://github.com/smartargs/mempolis")
@ManifestExtra(key = "Author", value = "SmartArgs")
@ManifestExtra(key = "Source", value = "https://github.com/smartargs/mempolis")
@ManifestExtra(key = "License", value = "Apache-2.0")
@Permission(nativeContract = NativeContract.ContractManagement, methods = "update")
@Permission(nativeContract = NativeContract.CryptoLib, methods = "verifyWithECDsa")
@Permission(nativeContract = NativeContract.StdLib, methods = {"base64Decode", "itoa"})
@Permission(nativeContract = NativeContract.GasToken, methods = "transfer")
@Permission(contract = "*", methods = {"numberOf", "isOpen"})
public class MempolisDeed {

    /** Phase before any claim. */
    public static final int PHASE_CLOSED = 0;
    /** Phase in which only founders claim. */
    public static final int PHASE_FOUNDERS = 1;
    /** Phase in which every wallet can claim. */
    public static final int PHASE_OPEN = 2;

    /** A standard lot, handed out by the queue. */
    public static final int TIER_STANDARD = 1;
    /** A choice lot, for sale at the choice price. */
    public static final int TIER_CHOICE = 2;
    /** A prime lot, for sale at the prime price. */
    public static final int TIER_PRIME = 3;
    /** A VIP lot, reserved. */
    public static final int TIER_VIP = 4;

    /** Price of a newcomer claim. */
    public static final int PRICE_NEWCOMER = 1;
    /** Price of a choice lot. */
    public static final int PRICE_CHOICE = 2;
    /** Price of a prime lot. */
    public static final int PRICE_PRIME = 3;
    /** Price of a change of house style. */
    public static final int PRICE_RESTYLE = 4;

    /** Payment data {@code [1]}: a newcomer claim. */
    public static final int PAY_NEWCOMER = 1;
    /** Payment data {@code [2, lot, style]}: a chosen claim. */
    public static final int PAY_CHOSEN = 2;
    /** Payment data {@code [3, style]}: a change of house style. */
    public static final int PAY_RESTYLE = 3;

    private static final int MAX_RING = 15;
    private static final int RING_SHIFT = 8;
    private static final int TIER_MASK = 7;
    private static final int MAX_LOT_INFO = 127;
    private static final int MAX_LOT = 65535;
    private static final int BYTE_MASK = 255;
    private static final int BYTE_VALUES = 256;
    private static final int PLACE_BYTES = 2;
    private static final int PLACE_PAD = 16777216;
    private static final int LOTS_PER_CHUNK = 200;
    private static final int PLACES_PER_CHUNK = 100;
    private static final int MAX_BATCH = 200;
    private static final int MAX_PAGE = 64;
    private static final int MAX_STYLES = 32;
    private static final int MAX_STATUS_BYTES = 112;
    private static final int MAX_LOOK_BYTES = 16;
    private static final int VOUCHER_CHARS = 88;
    private static final int DEPLOY_ARGS = 5;

    @DisplayName("Transfer")
    @EventParameterNames({"from", "to", "amount", "tokenId"})
    private static Event4Args<Hash160, Hash160, Integer, ByteString> onTransfer;

    @DisplayName("Claimed")
    @EventParameterNames({"wallet", "number", "lot", "style"})
    private static Event4Args<Hash160, Integer, Integer, Integer> onClaimed;

    @DisplayName("StyleChanged")
    @EventParameterNames({"number", "style"})
    private static Event2Args<Integer, Integer> onStyleChanged;

    @DisplayName("LookChanged")
    @EventParameterNames({"number", "look"})
    private static Event2Args<Integer, ByteString> onLookChanged;

    @DisplayName("StatusChanged")
    @EventParameterNames({"number", "status"})
    private static Event2Args<Integer, String> onStatusChanged;

    @DisplayName("LotsRegistered")
    @EventParameterNames({"first", "last"})
    private static Event2Args<Integer, Integer> onLotsRegistered;

    @DisplayName("LotsQueued")
    @EventParameterNames({"first", "last"})
    private static Event2Args<Integer, Integer> onLotsQueued;

    @DisplayName("PriceChanged")
    @EventParameterNames({"kind", "amount"})
    private static Event2Args<Integer, Integer> onPriceChanged;

    @DisplayName("RingOpened")
    @EventParameterNames({"ring"})
    private static Event1Arg<Integer> onRingOpened;

    @DisplayName("PhaseChanged")
    @EventParameterNames({"phase"})
    private static Event1Arg<Integer> onPhaseChanged;

    @DisplayName("StylesChanged")
    @EventParameterNames({"styles"})
    private static Event1Arg<Integer> onStylesChanged;

    @DisplayName("TreasuryChanged")
    @EventParameterNames({"treasury"})
    private static Event1Arg<Hash160> onTreasuryChanged;

    @DisplayName("GateChanged")
    @EventParameterNames({"gate"})
    private static Event1Arg<ECPoint> onGateChanged;

    /**
     * Stores the owner, gate key, guestbook, treasury and number of house styles on first deployment. An update leaves
     * them unchanged.
     *
     * @param data an array of the owner's script hash, the gate's public key, the guestbook's script hash, the
     *     treasury's script hash and the number of house styles
     * @param update whether this call comes from an update
     */
    @OnDeployment
    public static void deploy(Object data, boolean update) {
        if (update) return;
        if (data == null) Helper.abort("DD: bad deploy data");
        Object[] args = (Object[]) data;
        if (args.length != DEPLOY_ARGS) Helper.abort("DD: bad deploy data");
        if (!isAccount(args[0])) Helper.abort("DD: bad owner");
        if (!isPublicKey(args[1])) Helper.abort("DD: bad gate");
        if (!isAccount(args[2])) Helper.abort("DD: bad guestbook");
        if (!isAccount(args[3])) Helper.abort("DD: bad treasury");
        int styles = integerOf(args[4]);
        if (styles < 1 || styles > MAX_STYLES) Helper.abort("DD: bad styles");

        StorageContext ctx = Storage.getStorageContext();
        Storage.put(ctx, Keys.OWNER, (Hash160) args[0]);
        Storage.put(ctx, Keys.GATE, (ByteString) args[1]);
        Storage.put(ctx, Keys.GUESTBOOK, (Hash160) args[2]);
        Storage.put(ctx, Keys.TREASURY, (Hash160) args[3]);
        Storage.put(ctx, Keys.STYLES, styles);
    }

    /**
     * Claims a free plot for {@code wallet}, which must witness the transaction: the next lot of the queue, in the
     * free house style.
     *
     * @param wallet the wallet claiming
     * @param voucher the gate's signature over this contract's hash, {@code wallet} and the network's magic number, as
     *     64 bytes of r and s in base64; ignored for a founder
     * @return the claim number
     */
    public static int claim(Hash160 wallet, String voucher) {
        requireFirstCall();
        requireValidWallet(wallet);
        if (!Runtime.checkWitness(wallet)) Helper.abort("DD: no witness");
        int founder = requireMayClaim(wallet);
        if (founder == 0) requireVoucher(wallet, voucher);
        return mintFromQueue(wallet, founder);
    }

    /**
     * Takes GAS for a newcomer claim, a chosen claim or a change of house style, as {@code data} says, and forwards it
     * to the treasury. The amount must equal the price exactly.
     *
     * @param from the paying wallet, which becomes or holds the deed
     * @param amount the GAS paid, in fractions
     * @param data {@code [1]}, {@code [2, lot, style]} or {@code [3, style]}
     */
    @OnNEP17Payment
    public static void onNEP17Payment(Hash160 from, int amount, Object data) {
        requireFirstCall();
        if (!Runtime.getCallingScriptHash().equals(new GasToken().getHash())) Helper.abort("DD: only GAS");
        if (from == null || !Hash160.isValid(from) || from.isZero()) Helper.abort("DD: no sender");
        if (data == null) Helper.abort("DD: bad data");
        Object[] args = (Object[]) data;
        if (args.length == 0) Helper.abort("DD: bad data");
        int action = integerOf(args[0]);
        if (action == PAY_NEWCOMER && args.length == 1) {
            int founder = requireMayClaim(from);
            requirePrice(PRICE_NEWCOMER, amount);
            mintFromQueue(from, founder);
        } else if (action == PAY_CHOSEN && args.length == 3) {
            mintChosen(from, amount, integerOf(args[1]), integerOf(args[2]));
        } else if (action == PAY_RESTYLE && args.length == 2) {
            restyle(from, amount, integerOf(args[1]));
        } else {
            Helper.abort("DD: bad data");
        }
        forward(amount);
    }

    /**
     * Sets the resident's look of {@code wallet}'s deed, which creates its resident. The wallet must witness.
     *
     * @param wallet the deed's wallet
     * @param look 1 to 16 bytes, read by the site's art
     */
    public static void setLook(Hash160 wallet, ByteString look) {
        requireFirstCall();
        int number = requireOwnDeed(wallet);
        if (look == null || look.length() == 0 || look.length() > MAX_LOOK_BYTES) Helper.abort("DD: bad look");

        new StorageMap(Storage.getStorageContext(), Keys.LOOK_OF).put(number, look);

        onLookChanged.fire(number, look);
    }

    /**
     * Sets the speech-bubble line of {@code wallet}'s deed; an empty line clears it. The wallet must witness.
     *
     * @param wallet the deed's wallet
     * @param status up to 112 bytes of UTF-8; the site shows up to 28 characters
     */
    public static void setStatus(Hash160 wallet, String status) {
        requireFirstCall();
        int number = requireOwnDeed(wallet);
        if (status == null || status.length() > MAX_STATUS_BYTES) Helper.abort("DD: bad status");

        StorageMap statuses = new StorageMap(Storage.getStorageContext(), Keys.STATUS_OF);
        if (status.length() == 0) statuses.delete(number);
        else statuses.put(number, status);

        onStatusChanged.fire(number, status);
    }

    /**
     * Registers lots {@code first} onward. Owner only. Lots are numbered from 1 without gaps and never change once
     * registered.
     *
     * @param first the next lot number, one above {@link #lotCount()}
     * @param infos one byte per lot: its tier (1 to 4) plus 8 times its ring (0 to 15)
     */
    public static void registerLots(int first, ByteString infos) {
        requireFirstCall();
        requireOwner();
        if (first != lotCount() + 1) Helper.abort("DD: bad first lot");
        if (infos == null || infos.length() == 0 || infos.length() > MAX_BATCH) Helper.abort("DD: bad batch");
        int last = first + infos.length() - 1;
        if (last > MAX_LOT) Helper.abort("DD: registry full");
        for (int i = 0; i < infos.length(); i++) {
            int info = infos.get(i) & BYTE_MASK;
            int tier = info & TIER_MASK;
            if (info > MAX_LOT_INFO || tier < TIER_STANDARD || tier > TIER_VIP) Helper.abort("DD: bad lot info");
        }

        StorageContext ctx = Storage.getStorageContext();
        Chunks.append(new StorageMap(ctx, Keys.LOT_INFO), first - 1, infos, 1, LOTS_PER_CHUNK);
        Storage.put(ctx, Keys.LOTS, last);

        onLotsRegistered.fire(first, last);
    }

    /**
     * Appends standard lots to the free queue. Owner only. The queue is never reordered; a lot that is already taken
     * when its place comes up, such as one queued twice, is skipped.
     *
     * @param first the next queue place, one above {@link #queueLength()}
     * @param lots registered standard lots
     */
    public static void queueLots(int first, int[] lots) {
        requireFirstCall();
        requireOwner();
        if (first != queueLength() + 1) Helper.abort("DD: bad first place");
        if (lots == null || lots.length == 0 || lots.length > MAX_BATCH) Helper.abort("DD: bad batch");
        int registered = lotCount();
        StorageMap infos = new StorageMap(Storage.getReadOnlyContext(), Keys.LOT_INFO);
        Map<Integer, ByteString> read = new Map<>();
        ByteString places = null;
        for (int i = 0; i < lots.length; i++) {
            int lot = lots[i];
            if (lot < 1 || lot > registered) Helper.abort("DD: not a standard lot");
            int index = (lot - 1) / LOTS_PER_CHUNK + 1;
            if (!read.containsKey(index)) read.put(index, infos.get(index));
            if ((read.get(index).get((lot - 1) % LOTS_PER_CHUNK) & TIER_MASK) != TIER_STANDARD) Helper.abort("DD: not a standard lot");
            ByteString place = new ByteString(lot + PLACE_PAD).take(PLACE_BYTES);
            places = places == null ? place : places.concat(place);
        }

        StorageContext ctx = Storage.getStorageContext();
        Chunks.append(new StorageMap(ctx, Keys.QUEUE_AT), first - 1, places, PLACE_BYTES, PLACES_PER_CHUNK);
        int last = first + lots.length - 1;
        Storage.put(ctx, Keys.QUEUE_LENGTH, last);

        onLotsQueued.fire(first, last);
    }

    /**
     * Sets one price in GAS fractions; 0 stops that sale. Owner only.
     *
     * @param kind {@link #PRICE_NEWCOMER}, {@link #PRICE_CHOICE}, {@link #PRICE_PRIME} or {@link #PRICE_RESTYLE}
     * @param amount the price
     */
    public static void setPrice(int kind, int amount) {
        requireFirstCall();
        requireOwner();
        if (kind < PRICE_NEWCOMER || kind > PRICE_RESTYLE) Helper.abort("DD: bad price kind");
        if (amount < 0) Helper.abort("DD: bad amount");

        new StorageMap(Storage.getStorageContext(), Keys.PRICE).put(kind, amount);

        onPriceChanged.fire(kind, amount);
    }

    /**
     * Opens every ring up to {@code ring} for chosen claims. Owner only; rings only open. Free claims open the ring of
     * their lot themselves.
     *
     * @param ring a ring above {@link #openRing()}, at most 15
     */
    public static void openRingUpTo(int ring) {
        requireFirstCall();
        requireOwner();
        if (ring <= openRing() || ring > MAX_RING) Helper.abort("DD: bad ring");

        Storage.put(Storage.getStorageContext(), Keys.OPEN_RING, ring);

        onRingOpened.fire(ring);
    }

    /**
     * Moves to the next phase. Owner only; phases only move forward. The founders phase needs the guestbook closed.
     *
     * @param phase one above {@link #phase()}
     */
    public static void setPhase(int phase) {
        requireFirstCall();
        requireOwner();
        if (phase != phase() + 1 || phase > PHASE_OPEN) Helper.abort("DD: bad phase");
        if (phase == PHASE_FOUNDERS && (boolean) Contract.call(getGuestbook(), "isOpen", CallFlags.ReadOnly, new Object[0])) {
            Helper.abort("DD: guestbook open");
        }

        Storage.put(Storage.getStorageContext(), Keys.PHASE, phase);

        onPhaseChanged.fire(phase);
    }

    /**
     * Raises the number of house styles a chosen claim or restyle can pick. Owner only; styles are never removed.
     *
     * @param styles more than {@link #styleCount()}, at most 32
     */
    public static void setStyleCount(int styles) {
        requireFirstCall();
        requireOwner();
        if (styles <= styleCount() || styles > MAX_STYLES) Helper.abort("DD: bad styles");

        Storage.put(Storage.getStorageContext(), Keys.STYLES, styles);

        onStylesChanged.fire(styles);
    }

    /**
     * Sends every later payment to {@code treasury}. Owner only.
     *
     * @param treasury an account other than this contract
     */
    public static void setTreasury(Hash160 treasury) {
        requireFirstCall();
        requireOwner();
        if (!isAccount(treasury) || treasury.equals(Runtime.getExecutingScriptHash())) Helper.abort("DD: bad treasury");

        Storage.put(Storage.getStorageContext(), Keys.TREASURY, treasury);

        onTreasuryChanged.fire(treasury);
    }

    /**
     * Replaces the gate key, so vouchers from the old one stop working. Owner only.
     *
     * @param gate the new gate's public key
     */
    public static void setGate(ECPoint gate) {
        requireFirstCall();
        requireOwner();
        if (!isPublicKey(gate)) Helper.abort("DD: bad gate");

        Storage.put(Storage.getStorageContext(), Keys.GATE, gate.toByteString());

        onGateChanged.fire(gate);
    }

    /**
     * Replaces the contract's code and manifest and keeps its storage. Owner only.
     *
     * @param nef the new NEF file
     * @param manifest the new manifest as JSON, with the same contract name
     * @param data handed to the new code's deploy callback
     */
    public static void update(ByteString nef, String manifest, Object data) {
        requireFirstCall();
        requireOwner();
        new ContractManagement().update(nef, manifest, data);
    }

    /** Deeds cannot be transferred: always aborts. */
    public static boolean transfer(Hash160 to, ByteString tokenId, Object data) {
        Helper.abort("DD: soulbound");
        return false;
    }

    /** The token symbol. */
    @Safe
    public static String symbol() {
        return "DEED";
    }

    /** Deeds are indivisible. */
    @Safe
    public static int decimals() {
        return 0;
    }

    /** How many deeds exist, which is how many wallets have claimed. */
    @Safe
    public static int totalSupply() {
        return Storage.getIntOrZero(Storage.getReadOnlyContext(), Keys.CLAIMS);
    }

    /** 1 if {@code owner} holds a deed, else 0. */
    @Safe
    public static int balanceOf(Hash160 owner) {
        return numberOf(owner) == 0 ? 0 : 1;
    }

    /** The token ids {@code owner} holds: none or one. */
    @Safe
    public static Iterator<ByteString> tokensOf(Hash160 owner) {
        requireValidWallet(owner);
        ByteString prefix = new ByteString(Keys.NUMBER_OF).concat(owner);
        return (Iterator<ByteString>) Storage.find(Storage.getReadOnlyContext(), prefix, FindOptions.ValuesOnly);
    }

    /** Every token id, in the order of their storage keys, which is not claim order. */
    @Safe
    public static Iterator<ByteString> tokens() {
        return (Iterator<ByteString>) Storage.find(Storage.getReadOnlyContext(), Keys.WALLET_OF,
                FindOptions.KeysOnly | FindOptions.RemovePrefix);
    }

    /** The wallet holding token {@code tokenId}, or null. */
    @Safe
    public static Hash160 ownerOf(ByteString tokenId) {
        if (tokenId == null) Helper.abort("DD: bad token");
        return new StorageMap(Storage.getReadOnlyContext(), Keys.WALLET_OF).getHash160(tokenId);
    }

    /** The NEP-11 properties of token {@code tokenId}: name, lot, style and founder number. */
    @Safe
    public static Map<String, Object> properties(ByteString tokenId) {
        if (tokenId == null || ownerOf(tokenId) == null) Helper.abort("DD: no token");
        Deed deed = deedAt(tokenId.toInt());
        Map<String, Object> properties = new Map<>();
        properties.put("name", new ByteString("Mempolis Deed #").concat(new StdLib().itoa(deed.number, 10)).toString());
        properties.put("lot", deed.lot);
        properties.put("style", deed.style);
        properties.put("founder", deed.founder);
        return properties;
    }

    /** The claim number of {@code wallet}'s deed, or 0. */
    @Safe
    public static int numberOf(Hash160 wallet) {
        requireValidWallet(wallet);
        return new StorageMap(Storage.getReadOnlyContext(), Keys.NUMBER_OF).getIntOrZero(wallet);
    }

    /** The deed with claim number {@code number}, or null. */
    @Safe
    public static Deed deedAt(int number) {
        StorageContext ctx = Storage.getReadOnlyContext();
        Hash160 wallet = new StorageMap(ctx, Keys.WALLET_OF).getHash160(number);
        if (wallet == null) return null;
        return new Deed(number, wallet, new StorageMap(ctx, Keys.LOT_OF).getIntOrZero(number),
                new StorageMap(ctx, Keys.STYLE_OF).getIntOrZero(number),
                new StorageMap(ctx, Keys.FOUNDER_OF).getIntOrZero(number),
                new StorageMap(ctx, Keys.LOOK_OF).get(number),
                new StorageMap(ctx, Keys.STATUS_OF).getString(number));
    }

    /**
     * Up to {@code count} deeds from claim number {@code from} on, for reading the whole city page by page.
     *
     * @param from the first claim number, from 1
     * @param count 1 to 64
     */
    @Safe
    public static List<Deed> deeds(int from, int count) {
        if (from < 1 || count < 1 || count > MAX_PAGE) Helper.abort("DD: bad page");
        List<Deed> page = new List<>();
        int last = from + count - 1, supply = totalSupply();
        if (last > supply) last = supply;
        for (int number = from; number <= last; number++) page.add(deedAt(number));
        return page;
    }

    /** The tier of lot {@code lot}, or 0 if it is not registered. */
    @Safe
    public static int tierOf(int lot) {
        return lotInfo(lot) & TIER_MASK;
    }

    /** The ring of lot {@code lot}, counted from the centre; 0 also for an unregistered lot. */
    @Safe
    public static int ringOf(int lot) {
        return lotInfo(lot) / RING_SHIFT;
    }

    /** The claim number of the deed on lot {@code lot}, or 0 if the lot is free. */
    @Safe
    public static int claimOn(int lot) {
        return new StorageMap(Storage.getReadOnlyContext(), Keys.TAKEN).getIntOrZero(lot);
    }

    /** The lot the next free or newcomer claim gets, or 0 when the queue is used up. */
    @Safe
    public static int nextFreeLot() {
        return queueAt(nextFreePlace());
    }

    /** The lot at place {@code place} of the free queue, counted from 1, or 0. */
    @Safe
    public static int queueAt(int place) {
        if (place < 1 || place > queueLength()) return 0;
        ByteString chunk = new StorageMap(Storage.getReadOnlyContext(), Keys.QUEUE_AT).get((place - 1) / PLACES_PER_CHUNK + 1);
        int at = (place - 1) % PLACES_PER_CHUNK * PLACE_BYTES;
        return (chunk.get(at) & BYTE_MASK) + (chunk.get(at + 1) & BYTE_MASK) * BYTE_VALUES;
    }

    /** How many lots are registered. */
    @Safe
    public static int lotCount() {
        return Storage.getIntOrZero(Storage.getReadOnlyContext(), Keys.LOTS);
    }

    /** How many lots have ever been queued. */
    @Safe
    public static int queueLength() {
        return Storage.getIntOrZero(Storage.getReadOnlyContext(), Keys.QUEUE_LENGTH);
    }

    /** The outermost ring open to chosen claims. */
    @Safe
    public static int openRing() {
        return Storage.getIntOrZero(Storage.getReadOnlyContext(), Keys.OPEN_RING);
    }

    /** The current phase: {@link #PHASE_CLOSED}, {@link #PHASE_FOUNDERS} or {@link #PHASE_OPEN}. */
    @Safe
    public static int phase() {
        return Storage.getIntOrZero(Storage.getReadOnlyContext(), Keys.PHASE);
    }

    /** The price of {@code kind} in GAS fractions; 0 when that sale is stopped. */
    @Safe
    public static int priceOf(int kind) {
        return new StorageMap(Storage.getReadOnlyContext(), Keys.PRICE).getIntOrZero(kind);
    }

    /** How many house styles there are; styles are numbered from 0. */
    @Safe
    public static int styleCount() {
        return Storage.getIntOrZero(Storage.getReadOnlyContext(), Keys.STYLES);
    }

    /** The account that runs the registry, prices and phases and can update the contract. */
    @Safe
    public static Hash160 getOwner() {
        return Storage.getHash160(Storage.getReadOnlyContext(), Keys.OWNER);
    }

    /** The public key whose vouchers allow a free claim. */
    @Safe
    public static ECPoint getGate() {
        return new ECPoint(Storage.get(Storage.getReadOnlyContext(), Keys.GATE));
    }

    /** The founders' guestbook of this city. */
    @Safe
    public static Hash160 getGuestbook() {
        return Storage.getHash160(Storage.getReadOnlyContext(), Keys.GUESTBOOK);
    }

    /** Where every payment goes. */
    @Safe
    public static Hash160 getTreasury() {
        return Storage.getHash160(Storage.getReadOnlyContext(), Keys.TREASURY);
    }

    private static int requireMayClaim(Hash160 wallet) {
        int phase = phase();
        if (phase == PHASE_CLOSED) Helper.abort("DD: not open");
        if (numberOf(wallet) != 0) Helper.abort("DD: already claimed");
        int founder = (int) Contract.call(getGuestbook(), "numberOf", CallFlags.ReadOnly, new Object[]{wallet});
        if (phase == PHASE_FOUNDERS && founder == 0) Helper.abort("DD: founders only");
        return founder;
    }

    private static int mintFromQueue(Hash160 wallet, int founder) {
        int place = nextFreePlace();
        if (place == 0) Helper.abort("DD: no free lot");
        int lot = queueAt(place);
        int ring = ringOf(lot);
        boolean opens = ring > openRing();

        StorageContext ctx = Storage.getStorageContext();
        Storage.put(ctx, Keys.QUEUE_NEXT, place);
        if (opens) Storage.put(ctx, Keys.OPEN_RING, ring);
        int number = mint(ctx, wallet, lot, 0, founder);

        if (opens) onRingOpened.fire(ring);
        return number;
    }

    private static int nextFreePlace() {
        int length = queueLength();
        for (int place = Storage.getIntOrZero(Storage.getReadOnlyContext(), Keys.QUEUE_NEXT) + 1; place <= length; place++) {
            if (claimOn(queueAt(place)) == 0) return place;
        }
        return 0;
    }

    private static void mintChosen(Hash160 wallet, int amount, int lot, int style) {
        int founder = requireMayClaim(wallet);
        int tier = tierOf(lot);
        if (tier == 0) Helper.abort("DD: bad lot");
        if (tier == TIER_VIP) Helper.abort("DD: vip reserved");
        if (tier == TIER_STANDARD) Helper.abort("DD: lot not for sale");
        if (ringOf(lot) > openRing()) Helper.abort("DD: ring closed");
        if (claimOn(lot) != 0) Helper.abort("DD: lot taken");
        requireStyle(style);
        requirePrice(tier == TIER_CHOICE ? PRICE_CHOICE : PRICE_PRIME, amount);

        mint(Storage.getStorageContext(), wallet, lot, style, founder);
    }

    private static void restyle(Hash160 wallet, int amount, int style) {
        int number = numberOf(wallet);
        if (number == 0) Helper.abort("DD: no deed");
        requireStyle(style);
        StorageMap styles = new StorageMap(Storage.getStorageContext(), Keys.STYLE_OF);
        if (styles.getIntOrZero(number) == style) Helper.abort("DD: same style");
        requirePrice(PRICE_RESTYLE, amount);

        styles.put(number, style);

        onStyleChanged.fire(number, style);
    }

    private static int mint(StorageContext ctx, Hash160 wallet, int lot, int style, int founder) {
        int number = Storage.getIntOrZero(ctx, Keys.CLAIMS) + 1;
        Storage.put(ctx, Keys.CLAIMS, number);
        new StorageMap(ctx, Keys.NUMBER_OF).put(wallet, number);
        new StorageMap(ctx, Keys.WALLET_OF).put(number, wallet);
        new StorageMap(ctx, Keys.LOT_OF).put(number, lot);
        new StorageMap(ctx, Keys.TAKEN).put(lot, number);
        if (style != 0) new StorageMap(ctx, Keys.STYLE_OF).put(number, style);
        if (founder != 0) new StorageMap(ctx, Keys.FOUNDER_OF).put(number, founder);

        onTransfer.fire(null, wallet, 1, new ByteString(number));
        onClaimed.fire(wallet, number, lot, style);
        return number;
    }

    private static void forward(int amount) {
        if (!new GasToken().transfer(Runtime.getExecutingScriptHash(), getTreasury(), amount, null)) {
            Helper.abort("DD: forward failed");
        }
    }

    private static int lotInfo(int lot) {
        if (lot < 1 || lot > lotCount()) return 0;
        ByteString chunk = new StorageMap(Storage.getReadOnlyContext(), Keys.LOT_INFO).get((lot - 1) / LOTS_PER_CHUNK + 1);
        return chunk.get((lot - 1) % LOTS_PER_CHUNK) & BYTE_MASK;
    }

    private static void requirePrice(int kind, int amount) {
        int price = priceOf(kind);
        if (price == 0) Helper.abort("DD: not for sale");
        if (amount != price) Helper.abort("DD: wrong amount");
    }

    private static void requireStyle(int style) {
        if (style < 0 || style >= styleCount()) Helper.abort("DD: bad style");
    }

    private static int requireOwnDeed(Hash160 wallet) {
        requireValidWallet(wallet);
        if (!Runtime.checkWitness(wallet)) Helper.abort("DD: no witness");
        int number = numberOf(wallet);
        if (number == 0) Helper.abort("DD: no deed");
        return number;
    }

    private static void requireFirstCall() {
        if (Runtime.getInvocationCounter() != 1) Helper.abort("DD: re-entry");
    }

    private static void requireOwner() {
        if (!Runtime.checkWitness(getOwner())) Helper.abort("DD: not owner");
    }

    private static void requireVoucher(Hash160 wallet, String voucher) {
        if (voucher == null || voucher.length() != VOUCHER_CHARS) Helper.abort("DD: bad voucher");
        ByteString message = Runtime.getExecutingScriptHash().toByteString().concat(wallet.toByteString())
                .concat(Runtime.getNetwork());
        ByteString signature = new StdLib().base64Decode(voucher);
        if (!new CryptoLib().verifyWithECDsa(message, getGate(), signature, NamedCurveHash.secp256r1SHA256)) {
            Helper.abort("DD: bad voucher");
        }
    }

    private static void requireValidWallet(Hash160 wallet) {
        if (wallet == null || !Hash160.isValid(wallet) || wallet.isZero()) Helper.abort("DD: bad wallet");
    }

    private static int integerOf(Object value) {
        return ((ByteString) value).toInt();
    }

    private static boolean isPublicKey(Object value) {
        if (value == null || !ECPoint.isValid(value)) return false;
        int prefix = ((ECPoint) value).toByteString().get(0);
        return prefix == 2 || prefix == 3;
    }

    private static boolean isAccount(Object value) {
        return value != null && Hash160.isValid(value) && !((Hash160) value).isZero();
    }
}
