package sample;

import robocode.AdvancedRobot;
import robocode.ScannedRobotEvent;
import robocode.HitByBulletEvent;
import robocode.HitWallEvent;
import robocode.HitRobotEvent;
import robocode.WinEvent;
import robocode.Rules;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

import static robocode.util.Utils.normalRelativeAngle;


public class JaypeeNemesis extends AdvancedRobot {

    private static class EnemyInfo {
        double x, y, heading, velocity, distance;
        long lastSeen;
    }

    private final Map<String, EnemyInfo> enemies = new HashMap<>();
    private int moveDirection = 1;
    private static final double WALL_MARGIN = 80;

    public void run() {
        setBodyColor(Color.red);
        setGunColor(Color.red);
        setRadarColor(Color.red);
        setBulletColor(Color.yellow);

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);

        while (true) {
           
            setTurnRadarRightRadians(Double.POSITIVE_INFINITY);

            forgetStaleEnemies();
            moveSafely();
            execute();
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        double absoluteBearing = getHeadingRadians() + e.getBearingRadians();
        double distance = e.getDistance();
        double enemyX = getX() + distance * Math.sin(absoluteBearing);
        double enemyY = getY() + distance * Math.cos(absoluteBearing);

        EnemyInfo info = enemies.computeIfAbsent(e.getName(), k -> new EnemyInfo());
        info.x = enemyX;
        info.y = enemyY;
        info.heading = e.getHeadingRadians();
        info.velocity = e.getVelocity();
        info.distance = distance;
        info.lastSeen = getTime();

      
        if (nearestEnemy() != info) {
            return;
        }

        double radarTurn = normalRelativeAngle(absoluteBearing - getRadarHeadingRadians());
        setTurnRadarRightRadians(1.9 * radarTurn);

        fireAt(info);
    }

    private EnemyInfo nearestEnemy() {
        EnemyInfo nearest = null;
        for (EnemyInfo info : enemies.values()) {
            if (nearest == null || info.distance < nearest.distance) {
                nearest = info;
            }
        }
        return nearest;
    }

    private void forgetStaleEnemies() {
        long now = getTime();
        enemies.values().removeIf(info -> now - info.lastSeen > 30);
    }

    private void fireAt(EnemyInfo info) {
        if (getEnergy() < 1.0) {
            return; 

        double distance = info.distance;
        double bulletPower = distance < 200 ? 3.0 : distance < 400 ? 2.0 : 1.2;
        bulletPower = Math.min(bulletPower, getEnergy() - 0.5);
        if (bulletPower < 0.1) {
            return;
        }

        double bulletSpeed = Rules.getBulletSpeed(bulletPower);

        double predictedX = info.x;
        double predictedY = info.y;
        for (int i = 0; i < 10; i++) {
            double dx = predictedX - getX();
            double dy = predictedY - getY();
            double dist = Math.hypot(dx, dy);
            long time = Math.round(dist / bulletSpeed);
            predictedX = info.x + Math.sin(info.heading) * info.velocity * time;
            predictedY = info.y + Math.cos(info.heading) * info.velocity * time;
        }

        double gunAngle = Math.atan2(predictedX - getX(), predictedY - getY());
        setTurnGunRightRadians(normalRelativeAngle(gunAngle - getGunHeadingRadians()));

        if (getGunHeat() == 0 && Math.abs(getGunTurnRemainingRadians()) < Math.toRadians(10)) {
            setFire(bulletPower);
        }
    }
    }

    private void moveSafely() {
        double fx = 0, fy = 0;
        double myX = getX();
        double myY = getY();

        
        for (EnemyInfo info : enemies.values()) {
            double dx = myX - info.x;
            double dy = myY - info.y;
            double dist = Math.max(Math.hypot(dx, dy), 1);
            double weight = 8000 / (dist * dist);
            fx += (dx / dist) * weight;
            fy += (dy / dist) * weight;
        }

       
        double fieldW = getBattleFieldWidth();
        double fieldH = getBattleFieldHeight();
        fx += wallForce(myX) - wallForce(fieldW - myX);
        fy += wallForce(myY) - wallForce(fieldH - myY);

       
        double tangentAngle = Math.atan2(fy, fx) + (Math.PI / 2) * moveDirection;
        fx += Math.cos(tangentAngle) * 40;
        fy += Math.sin(tangentAngle) * 40;

        if (fx == 0 && fy == 0) {
            return; 
        }

        double angle = Math.atan2(fx, fy);
        double turn = normalRelativeAngle(angle - getHeadingRadians());

        double moveDist = 100;
        if (Math.abs(turn) > Math.PI / 2) {
            turn = normalRelativeAngle(turn - Math.PI);
            moveDist = -moveDist;
        }

        setTurnRightRadians(turn);
        setAhead(moveDist);

        if (Math.random() < 0.03) {
            moveDirection = -moveDirection;
        }
    }

    private double wallForce(double distanceFromEdge) {
        if (distanceFromEdge >= WALL_MARGIN) {
            return 0;
        }
        return (WALL_MARGIN - distanceFromEdge) * 4;
    }

    public void onHitByBullet(HitByBulletEvent e) {
        moveDirection = -moveDirection;
    }

    public void onHitWall(HitWallEvent e) {
  
        moveDirection = -moveDirection;
        setBack(60 * moveDirection);
    }

    public void onHitRobot(HitRobotEvent e) {
        if (getEnergy() > 1.0) {
            setFire(Math.min(3.0, getEnergy() - 0.5));
        }
        setBack(60);
    }

    public void onWin(WinEvent e) {
        for (int i = 0; i < 50; i++) {
            turnRight(30);
            turnLeft(30);
        }
    }
}