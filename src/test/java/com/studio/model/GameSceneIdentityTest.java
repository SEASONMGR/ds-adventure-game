package com.studio.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GameScene} 节点「同一性」语义回归（PR #16 的选中一致性修复）。
 *
 * <p>背景：{@link StoryNode#equals(Object)} 是<b>按内容</b>比较的（type + id + x + y）。
 * 两个不同场景里"同类型、同 id、同坐标"的节点会被判定为相等 —— 于是
 * {@code List.indexOf}/{@code contains} 会命中<b>另一个场景</b>的同款节点，
 * 表现为"在场景 2 点节点，却跳到场景 1 并选中同款节点"。</p>
 *
 * <p>本测试把两条语义都钉住：内容相等 ≠ 同一个对象；涉及"选中/删除/层级"的操作必须走
 * {@link GameScene#indexOfIdentity(StoryNode)}。</p>
 */
class GameSceneIdentityTest {

    private static StoryNode node(NodeType type, String id, double x, double y) {
        StoryNode n = new StoryNode();
        n.setType(type);
        n.setId(id);
        n.setX(x);
        n.setY(y);
        return n;
    }

    @Test
    void equalContentNodesAreNotSameIdentity() {
        StoryNode a = node(NodeType.TEXT, "灯", 100, 100);
        StoryNode b = node(NodeType.TEXT, "灯", 100, 100);
        // 内容相等：这是 equals 的设计（用于内容比较/去重判断）
        assertEquals(a, b, "同类型+同 id+同坐标 → equals 认为相等（既有设计）");
        // 但不是同一个对象
        assertFalse(a == b);
    }

    @Test
    void indexOfIdentityFindsTheRightInstance() {
        GameScene scene = new GameScene("s1");
        StoryNode first = node(NodeType.TEXT, "灯", 100, 100);
        StoryNode twin = node(NodeType.TEXT, "灯", 100, 100);     // 内容相同、另一实例
        scene.addNode(first);

        assertEquals(0, scene.indexOfIdentity(first), "按同一性应命中下标 0");
        assertEquals(-1, scene.indexOfIdentity(twin), "内容相同的另一个实例不应被命中");
    }

    @Test
    void containsAndRemoveUseIdentity() {
        GameScene scene = new GameScene("s1");
        StoryNode only = node(NodeType.CHARACTER, "角色", 10, 20);
        StoryNode stranger = node(NodeType.CHARACTER, "角色", 10, 20);
        scene.addNode(only);

        assertTrue(scene.contains(only), "自己当然在场景里");
        assertFalse(scene.contains(stranger), "内容相同但非同一实例 → 不应被认为在场景里");

        scene.removeNode(stranger);                        // 不应误删
        assertEquals(1, scene.nodes().size(), "删'同款但非同一实例'不应动到真正的节点");
        scene.removeNode(only);
        assertEquals(0, scene.nodes().size(), "删自己应生效");
    }

    @Test
    void sameContentInDifferentScenesDoNotLeak() {
        GameScene s1 = new GameScene("s1");
        GameScene s2 = new GameScene("s2");
        StoryNode n1 = node(NodeType.TEXT, "标签", 5, 5);
        StoryNode n2 = node(NodeType.TEXT, "标签", 5, 5);
        s1.addNode(n1);
        s2.addNode(n2);

        assertSame(n1, s1.nodes().get(0));
        assertSame(n2, s2.nodes().get(0));
        assertEquals(0, s1.indexOfIdentity(n1), "场景 1 里按同一性找自己");
        assertEquals(0, s2.indexOfIdentity(n2), "场景 2 里按同一性找自己");
        assertEquals(-1, s1.indexOfIdentity(n2), "场景 1 里不该找到场景 2 的同款节点（原 bug）");
    }
}
