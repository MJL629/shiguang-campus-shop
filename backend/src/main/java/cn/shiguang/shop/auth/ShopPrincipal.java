// AI-assisted implementation with Codex.
package cn.shiguang.shop.auth;
import java.io.Serializable;
public record ShopPrincipal(long id, String username, String role) implements Serializable {}
