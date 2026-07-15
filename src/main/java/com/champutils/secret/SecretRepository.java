package com.champutils.secret;

import com.champutils.database.DatabaseManager;
import java.sql.*;
import java.util.*;

public final class SecretRepository {
    private SecretRepository() {}
    public record Progress(String secretId, int completedStep, boolean completed) {}
    public static void ensureSchema(Connection c) throws SQLException {
        try (Statement s=c.createStatement()) {
            s.executeUpdate("create table if not exists player_secret_progress (player_uuid uuid not null, secret_id text not null, completed_step integer not null default 0, completed boolean not null default false, updated_at timestamptz not null default now(), completed_at timestamptz, primary key(player_uuid, secret_id))");
            s.executeUpdate("create index if not exists idx_player_secret_progress_player on player_secret_progress(player_uuid)");
        }
    }
    public static Map<String,Progress> load(UUID player) {
        Map<String,Progress> out=new HashMap<>(); if(!DatabaseManager.isEnabled()) return out;
        try { Connection c=DatabaseManager.getConnection(); ensureSchema(c); try(PreparedStatement ps=c.prepareStatement("select secret_id,completed_step,completed from player_secret_progress where player_uuid=?")){ ps.setObject(1,player); try(ResultSet rs=ps.executeQuery()){ while(rs.next()){ String id=rs.getString(1); out.put(id,new Progress(id,rs.getInt(2),rs.getBoolean(3))); } } } } catch(Exception e){ System.err.println("[ChampUtils] Failed loading secret progress: "+e.getMessage()); }
        return out;
    }
    public static boolean advance(Connection c, UUID player, String secret, int expectedPrevious, int newStep, boolean complete) throws SQLException {
        ensureSchema(c); boolean oldAuto=c.getAutoCommit(); c.setAutoCommit(false);
        try {
            int current=0; boolean already=false;
            try(PreparedStatement lock=c.prepareStatement("select completed_step,completed from player_secret_progress where player_uuid=? and secret_id=? for update")){ lock.setObject(1,player); lock.setString(2,secret); try(ResultSet rs=lock.executeQuery()){ if(rs.next()){ current=rs.getInt(1); already=rs.getBoolean(2); } } }
            if(already || current!=expectedPrevious){ c.rollback(); return false; }
            try(PreparedStatement up=c.prepareStatement("insert into player_secret_progress(player_uuid,secret_id,completed_step,completed,updated_at,completed_at) values(?,?,?,?,now(),case when ? then now() else null end) on conflict(player_uuid,secret_id) do update set completed_step=excluded.completed_step,completed=excluded.completed,updated_at=now(),completed_at=case when excluded.completed then coalesce(player_secret_progress.completed_at,now()) else player_secret_progress.completed_at end")){
                up.setObject(1,player); up.setString(2,secret); up.setInt(3,newStep); up.setBoolean(4,complete); up.setBoolean(5,complete); up.executeUpdate();
            }
            c.commit(); return true;
        } catch(SQLException e){ c.rollback(); throw e; } finally { c.setAutoCommit(oldAuto); }
    }
    public static int completedCount(Connection c, UUID player) throws SQLException { ensureSchema(c); try(PreparedStatement ps=c.prepareStatement("select count(*) from player_secret_progress where player_uuid=? and completed=true")){ ps.setObject(1,player); try(ResultSet rs=ps.executeQuery()){ return rs.next()?rs.getInt(1):0; } } }
}
