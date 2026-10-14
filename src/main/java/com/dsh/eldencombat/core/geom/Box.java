package com.dsh.eldencombat.core.geom;

/** 轴对齐包围盒（世界坐标，单位方块）。 */
public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

    public Box {
        if (maxX < minX || maxY < minY || maxZ < minZ) {
            throw new IllegalArgumentException("非法 AABB: " + minX + "," + minY + "," + minZ + " -> " + maxX + "," + maxY + "," + maxZ);
        }
    }

    public double centerX() {
        return (minX + maxX) * 0.5;
    }

    public double centerY() {
        return (minY + maxY) * 0.5;
    }

    public double centerZ() {
        return (minZ + maxZ) * 0.5;
    }

    /** 盒到点的最近距离（点在盒内为 0）。 */
    public double distanceTo(double x, double y, double z) {
        double dx = Math.max(Math.max(minX - x, 0), x - maxX);
        double dy = Math.max(Math.max(minY - y, 0), y - maxY);
        double dz = Math.max(Math.max(minZ - z, 0), z - maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public Box expand(double amount) {
        return new Box(minX - amount, minY - amount, minZ - amount, maxX + amount, maxY + amount, maxZ + amount);
    }

    public Box inflate(double x, double y, double z) {
        return new Box(minX - x, minY - y, minZ - z, maxX + x, maxY + y, maxZ + z);
    }
}
