
import java.util.Calendar;
import java.util.Enumeration;
import java.util.Hashtable;

public final class AThMadaraFunc {
    private static final int TELEPORT_MAX_TILES = 6;
    // J2ME CLDC: dùng Hashtable (raw) thay cho java.util.Map/HashMap
    private static Hashtable gomSavedGlobal = null;
    private static boolean gomFollowPlayer = false;
    private static int gomMapId = -999999;
    static boolean gomUseMapCenter = false;
    public static int gomOffsetX = 0;
    public static int gomOffsetY = 0;
    public static int gomFixedX = 0;
    public static int gomFixedY = 0;
    public static void autoFireMoveToTarget(Player p) {
        if (p == null) return;
        if (p.skillCurrent != null) return;
        try {
            if (Player.typeAutoBuff == 1 && MsgAutoFire.value != null) {
                    for (int i = 0; i < MsgAutoFire.value.length; i++) {
                        short id = MsgAutoFire.value[i][0];
                        short enabled = MsgAutoFire.value[i][1];
                        if (enabled != 1) continue;
                        Skill_Info sk = Skill_Info.getSkillFromID(id);
                        if (sk == null) continue;
                        if (p.getManaNeedUse((int) sk.manaLost) > p.Mp) continue;
                        if (!DelaySkill.getDelay(sk.indexHotKey).isCoolDown()) continue;
                        try {
                            boolean started = p.beginPlayerFire(sk);
                            if (started) {
                                p.timeFristSkill = GameCanvas.timeNow;
                                return;
                            }
                        } catch (Throwable ex) { ex.printStackTrace(); }
                }
            }
        } catch (Throwable t) { t.printStackTrace(); }

        if (gomSavedGlobal != null && gomUseMapCenter) {
            try {
                p.x = gomFixedX;
                p.y = gomFixedY;
                p.toX = gomFixedX; p.toY = gomFixedY;
                p.toXNew = gomFixedX; p.toYNew = gomFixedY;
                p.vx = 0; p.vy = 0;
                p.posTransRoad = null;
            } catch (Throwable ignored) {}
        }

        if (!p.isAutoFireNew108) return;

        MainObject tgt = GameScreen.objFocus;
        // Chỉ cho phép: quái/boss. Nếu focus hiện tại không hợp lệ, bỏ focus và tìm lại.
        if (tgt == null || !isAllowedTargetForAuto(p, tgt)) {
            GameScreen.objFocus = null;
            // tìm mục tiêu hợp lệ gần nhất
            int best = Integer.MAX_VALUE;
            MainObject bestObj = null;
            for (int i = 0; i < GameScreen.vecPlayers.size(); i++) {
                MainObject o = (MainObject) GameScreen.vecPlayers.elementAt(i);
                if (o == null) continue;
                if (!isAllowedTargetForAuto(p, o)) continue;
                if (o.isRemove || o.isDie || o.Hp <= 0) continue;
                int d = MainObject.getDistance(p.x, p.y, o.x, o.y);
                if (d < best) {
                    best = d;
                    bestObj = o;
                }
            }
            if (bestObj != null) {
                GameScreen.objFocus = bestObj;
                tgt = bestObj;
            } else {
                return;
            }
        }

        if (tgt.isRemove || tgt.Hp <= 0 || tgt.isDie) {
            try { p.nextMonster(); } catch (Throwable ignored) {}
            tgt = GameScreen.objFocus;
            if (tgt == null || !isAllowedTargetForAuto(p, tgt)) return;
        }
        // Nếu gom theo center thì bỏ qua kiểm tra attackable; còn lại phải hợp lệ
        if (!isAllowedTargetForAuto(p, tgt) && (gomSavedGlobal == null || !gomUseMapCenter)) return;

        Hotkey[] row = p.hotkeyPlayer != null ? p.hotkeyPlayer[p.currentTab] : null;
        if (row == null || row.length == 0) return;
        int n = row.length;
        boolean isAutoNew = p.isAutoFireNew108;
        if (isAutoNew) {
            if (p.IndexFire < 0 || p.IndexFire >= n) p.IndexFire = 0;
        }

        int tileSize = GameCanvas.loadmap != null && GameCanvas.loadmap.wTile > 0
                       ? GameCanvas.loadmap.wTile
                       : (LoadMap.wTile > 0 ? LoadMap.wTile : 24);
        // Tính bình phương khoảng cách để tránh Math.sqrt() (J2ME CLDC 1.0 không hỗ trợ)
        long teleportMaxPixels = (long) TELEPORT_MAX_TILES * tileSize;
        long teleportMaxSq = teleportMaxPixels * teleportMaxPixels;

        for (int iter = 0; iter < n; ++iter) {
            int slot = isAutoNew ? ((p.IndexFire + iter) % n) : iter;
            Hotkey hk = row[slot];
            if (hk == null) continue;
            if (hk.itemcur != null) {
                try {
                    p.setActionHotKey(slot);
                    if (isAutoNew) p.IndexFire = (slot + 1) % n;
                } catch (Throwable ex) { ex.printStackTrace(); }
                break;
            }
            if (hk.skill != null) {
                Skill_Info sk = Skill_Info.getSkillFromID(hk.skill.ID);
                if (sk == null) continue;
                if (!DelaySkill.getDelay(sk.indexHotKey).isCoolDown()) continue;
                if (p.getManaNeedUse((int) sk.manaLost) > p.Mp) continue;
                if (sk.typeSkill == 2) {
                    try {
                        p.setActionHotKey(slot);
                        p.timeFristSkill = GameCanvas.timeNow;
                        if (isAutoNew) p.IndexFire = (slot + 1) % n;
                    } catch (Throwable ex) { ex.printStackTrace(); }
                    break;
                }
                if (gomSavedGlobal != null && gomUseMapCenter) {
                    try {
                        p.setActionHotKey(slot);
                        p.timeFristSkill = GameCanvas.timeNow;
                        if (isAutoNew) p.IndexFire = (slot + 1) % n;
                    } catch (Throwable ex) { ex.printStackTrace(); }
                    break;
                }
                long dx = p.x - tgt.x;
                long dy = p.y - tgt.y;
                long distSq = dx * dx + dy * dy;
                // teleport khi quá xa (distSq > maxSq) - so sánh bình phương
                boolean doTeleport = distSq > teleportMaxSq;

                if (doTeleport) {
                    int tx = (tgt.x / tileSize) * tileSize + tileSize / 2;
                    int ty = (tgt.y / tileSize) * tileSize + tileSize / 2;
                    sendTeleport(p, tx, ty);
                    p.posTransRoad = null;
                    try { p.vx = 0; p.vy = 0; } catch (Throwable ignored) {}
                }

                try {
                    p.setActionHotKey(slot);
                    p.timeFristSkill = GameCanvas.timeNow;
                    if (isAutoNew) p.IndexFire = (slot + 1) % n;
                } catch (Throwable ex) { ex.printStackTrace(); }
                break;
            }
        }
    }

    private static boolean isAllowedTargetForAuto(Player p, MainObject t) {
        return t != null && t.typeObject == 1;
    }

    public static void updateAutoGom(Player p, boolean enabled) {
        if (!enabled) {
            stopGomAll(null);
            return;
        }
        if (p == null || GameScreen.vecPlayers == null) return;
        // Tick guard: chỉ chạy 1 lần / 4 ticks (~7 lần/giây) đủ cho cảm giác mượt
        if (GameCanvas.gameTick % 4 != 0) return;
        try {
            int mapId = GameCanvas.loadmap != null ? GameCanvas.loadmap.idMapLoadMap : -1;
            if (gomSavedGlobal == null || gomMapId != mapId) {
                if (gomSavedGlobal != null) gomSavedGlobal.clear();
                gomSavedGlobal = new Hashtable();
                gomMapId = mapId;
            }

            int targetX;
            int targetY;
            if (GameCanvas.loadmap != null) {
                targetX = GameCanvas.loadmap.maxWMap / 2;
                targetY = GameCanvas.loadmap.maxHMap / 2;
            } else {
                targetX = p.x;
                targetY = p.y;
            }
            gomFixedX = targetX;
            gomFixedY = targetY;
            gomUseMapCenter = true;

            for (int i = 0; i < GameScreen.vecPlayers.size(); ++i) {
                Object o = GameScreen.vecPlayers.elementAt(i);
                if (!(o instanceof MainMonster)) continue;
                MainMonster m = (MainMonster)o;
                if (m == null || m.isRemove || m.isDie || m.Hp <= 0) continue;
                if (!gomSavedGlobal.containsKey(m)) {
                    gomSavedGlobal.put(m, new int[]{m.x, m.y, m.toX, m.toY, m.toXNew, m.toYNew, m.vx, m.vy});
                }
                m.gomX = targetX;
                m.gomY = targetY;
                m.x = targetX;
                m.y = targetY;
                m.toX = targetX;
                m.toY = targetY;
                m.toXNew = targetX;
                m.toYNew = targetY;
                m.vx = 0;
                m.vy = 0;
                m.isGom = true;
            }

            if (p.isAutoFireNew108) {
                p.x = targetX;
                p.y = targetY;
                p.toX = targetX;
                p.toY = targetY;
                p.toXNew = targetX;
                p.toYNew = targetY;
                p.vx = 0;
                p.vy = 0;
                p.posTransRoad = null;
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static Hashtable startGomAllFollow(Player p, int offsetX, int offsetY, boolean follow, boolean useMapCenter) {
        Hashtable saved = new Hashtable();
        if (p == null) return saved;

        try {
            mVector vec = GameScreen.vecPlayers;
            if (vec == null) return saved;

            int targetX;
            int targetY;
            if (useMapCenter && GameCanvas.loadmap != null) {
                int maxW = GameCanvas.loadmap.maxWMap;
                int maxH = (int) (GameCanvas.loadmap.maxHMap >= 0 ? GameCanvas.loadmap.maxHMap : (GameCanvas.h > 0 ? GameCanvas.h : maxW));
                targetX = maxW / 2 + offsetX;
                targetY = maxH / 2 + offsetY;
                gomFixedX = targetX; gomFixedY = targetY;
                gomUseMapCenter = true;
            } else {
                targetX = p.x + offsetX;
                targetY = p.y + offsetY;
                gomUseMapCenter = false;
            }

            for (int i = 0; i < vec.size(); ++i) {
                Object o = vec.elementAt(i);
                if (o instanceof MainMonster) {
                    MainMonster m = (MainMonster) o;
                    if (m == null || m.isRemove) continue;
                    try {
                        saved.put(m, new int[]{m.x, m.y, m.toX, m.toY, m.toXNew, m.toYNew, m.vx, m.vy});
                    } catch (Throwable ignored) {}
                    m.gomX = targetX; m.gomY = targetY;
                    m.x = targetX; m.y = targetY;
                    m.toX = targetX; m.toY = targetY;
                    m.toXNew = targetX; m.toYNew = targetY;
                    m.vx = 0; m.vy = 0;
                    m.isGom = true;
                }
            }

            gomSavedGlobal = saved;
            gomFollowPlayer = follow;
            gomOffsetX = offsetX;
            gomOffsetY = offsetY;
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return saved;
    }

    public static void updateGomPositions(Player p) {
        if (p == null || gomSavedGlobal == null) return;
        try {
            int targetX;
            int targetY;
            if (gomUseMapCenter) {
                targetX = gomFixedX;
                targetY = gomFixedY;
            } else if (gomFollowPlayer) {
                targetX = p.x + gomOffsetX;
                targetY = p.y + gomOffsetY;
            } else {
                Enumeration enFirst = gomSavedGlobal.keys();
                if (!enFirst.hasMoreElements()) return;
                MainMonster first = (MainMonster) enFirst.nextElement();
                if (first == null) return;
                targetX = first.gomX;
                targetY = first.gomY;
            }

            Enumeration en = gomSavedGlobal.keys();
            while (en.hasMoreElements()) {
                MainMonster m = (MainMonster) en.nextElement();
                if (m == null) continue;
                m.gomX = targetX; m.gomY = targetY;
                m.x = targetX; m.y = targetY;
                m.toX = targetX; m.toY = targetY;
                m.toXNew = targetX; m.toYNew = targetY;
                m.vx = 0; m.vy = 0;
                m.isGom = true;
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void stopGomAll(Hashtable saved) {
        Hashtable toRestore = saved != null ? saved : gomSavedGlobal;
        if (toRestore == null) return;
        try {
            Enumeration en = toRestore.keys();
            while (en.hasMoreElements()) {
                MainMonster m = (MainMonster) en.nextElement();
                int[] s = (int[]) toRestore.get(m);
                if (m == null || s == null || s.length < 8) continue;
                try {
                    m.x = s[0]; m.y = s[1];
                    m.toX = s[2]; m.toY = s[3];
                    m.toXNew = s[4]; m.toYNew = s[5];
                    m.vx = s[6]; m.vy = s[7];
                } catch (Throwable ignored) {}
                m.isGom = false;
                m.gomX = 0; m.gomY = 0;
            }
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            if (gomSavedGlobal != null) gomSavedGlobal.clear();
            gomSavedGlobal = null;
            gomFollowPlayer = false;
            gomUseMapCenter = false;
            gomMapId = -999999;
            gomOffsetX = gomOffsetY = 0;
            gomFixedX = gomFixedY = 0;
        }
    }
    private static boolean isAttackable(MainObject t) {
        return t != null && !t.returnAction() && !t.isDie && t.Hp > 0 && !t.isRemove;
    }

    // Cooldown giữa hai lần teleport để tránh flood Sender queue
    private static long lastTeleportAt = 0L;
    private static final long TELEPORT_COOLDOWN_MS = 250L;

    private static void sendTeleport(Player p, int x, int y) {
        long now;
        try { now = GameCanvas.timeNow; } catch (Throwable t) { now = System.currentTimeMillis(); }
        if (now - lastTeleportAt < TELEPORT_COOLDOWN_MS) return;
        lastTeleportAt = now;
        try {
            GlobalService.getInstance().Obj_Move((short) x, (short) y);
            p.x = x; p.y = y;
            try { p.CX = x; p.CY = y; p.isSendMove = true; } catch (Throwable ignored) {}
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
   
   private static long lastDateTimeBuiltAtSec = -1L;
   private static String cachedDateTimeStr = "";

   public static void paintShowDateTime(mGraphics g) {
        try {
            long nowSec = System.currentTimeMillis() / 1000L;
            // Chỉ rebuild string khi giây đổi -> tránh tạo rác mỗi paint frame
            if (nowSec != lastDateTimeBuiltAtSec) {
                lastDateTimeBuiltAtSec = nowSec;
                Calendar cal = CRes.getTime();
                int hh  = cal.get(Calendar.HOUR_OF_DAY);
                int mm  = cal.get(Calendar.MINUTE);
                int ss  = cal.get(Calendar.SECOND);
                int dd  = cal.get(Calendar.DAY_OF_MONTH);
                int mon = cal.get(Calendar.MONTH) + 1;
                int yy  = cal.get(Calendar.YEAR);
                StringBuffer sb = new StringBuffer(20);
                pad2(sb, hh); sb.append(':');
                pad2(sb, mm); sb.append(':');
                pad2(sb, ss); sb.append(' ');
                pad2(sb, dd); sb.append('-');
                pad2(sb, mon); sb.append('-');
                sb.append(yy);
                cachedDateTimeStr = sb.toString();
            }
            String timeStr = cachedDateTimeStr;
            if (timeStr == null || timeStr.length() == 0) return;
            int imgW = 22, imgH = 16;
            int chatX = Interface_Game.xNumMess;
            int chatY = Interface_Game.yNumMess;
            try {
                chatY += Interface_Game.numMess.yNum;
            } catch (Throwable ignored) {}

            int textY = chatY + imgH + 4;
            int textW = mFont.tahoma_7_white.getWidth(timeStr);
            int drawX = chatX + (imgW / 2) - (textW / 2);
            if (drawX < 2) drawX = 2;
            if (drawX + textW > MotherCanvas.w - 2) {
                drawX = MotherCanvas.w - 2 - textW;
            }
            mFont.tahoma_7_white.drawString(g, timeStr, drawX, textY, 0);

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
   
   public static void autoSellItem(Player p) {
       //để đây code sau
   }

    // Trong AThMadaraFunc.autoReviceV1
    public static void autoReviceV1(Player p) {
        if (p == null || !p.isDie || !p.isAutoRevice) return;
        try {
            // Lưu ID map và toạ độ hiện tại (để quay lại đúng vị trí)
            if (GameCanvas.loadmap != null) {
                AThMadaraMOD.lastMapId = GameCanvas.loadmap.idMapLoadMap;
            }
            if (GameScreen.player != null) {
                AThMadaraMOD.lastX = GameScreen.player.x;
                AThMadaraMOD.lastY = GameScreen.player.y;
            }
            // Lưu khu hiện tại nếu có (dùng message 23 để set lại)
            try {
                AThMadaraFunc.saveCurrentZone();
            } catch (Throwable ignored) {}

            // Bật cờ: sau hồi sinh → quay lại map cũ
            AThMadaraMOD.shouldReturnToLastMap = true;

            // Gửi hồi sinh về làng
            GlobalService.getInstance().Player_Revice((byte)0);
            Interface_Game.addInfoPlayerNormal("Đã hồi sinh. Sẽ quay lại map cũ...", mFont.tahoma_7_yellow);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Lưu khu hiện tại (nếu server/phía client có khái niệm khu). Ở đây ta đọc từ giao thức nếu có.
    private static byte savedZone = -1;
    public static void saveCurrentZone() {
        // Không tìm thấy biến zone trong client, nhưng có message 23 dùng để đổi khu.
        // Ta không thể đọc khu hiện tại từ client ⇒ dựa vào server-side if any.
        // Mặc định giữ -1 nếu không xác định được.
        // Nếu bạn có biến current zone, gán nó vào savedZone ở đây.
    }

    public static void restoreSavedZoneIfAny() {
        if (savedZone >= 0 && savedZone <= 9) {
            try {
                GlobalService.getInstance().AB((byte)0, savedZone);
            } catch (Throwable ignored) {}
        }
    }

    // Helper: append "%02d" vào StringBuffer (thay String.format) - không tạo rác
    private static void pad2(StringBuffer sb, int v) {
        if (v < 0) { sb.append('-'); v = -v; }
        if (v < 10) sb.append('0');
        sb.append(v);
    }

}
