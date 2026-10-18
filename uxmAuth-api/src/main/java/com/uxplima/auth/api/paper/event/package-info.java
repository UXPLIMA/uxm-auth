/**
 * The Bukkit events uxmAuth fires on Paper and Folia. The ones before a login or a registration can be cancelled
 * with a reason, and fire off the server's threads, as does every event about an account rather than a player in
 * the world; {@link com.uxplima.auth.api.paper.event.LoginEvent} and
 * {@link com.uxplima.auth.api.paper.event.RegisterEvent} fire on the player's own thread.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.auth.api.paper.event;
