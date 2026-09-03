import { createRouter, createWebHashHistory } from "vue-router";
import { getToken } from "./api";

export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: "/login", component: () => import("./views/Login.vue") },
    {
      path: "/",
      component: () => import("./views/Layout.vue"),
      redirect: "/songs",
      children: [
        { path: "songs", component: () => import("./views/Songs.vue") },
        { path: "categories", component: () => import("./views/Categories.vue") },
        { path: "artists", component: () => import("./views/Artists.vue") },
        { path: "albums", component: () => import("./views/Albums.vue") },
        { path: "banners", component: () => import("./views/Banners.vue") },
        { path: "playlists", component: () => import("./views/Playlists.vue") },
        { path: "trending", component: () => import("./views/Trending.vue") },
        { path: "users", component: () => import("./views/Users.vue") },
        { path: "reports", component: () => import("./views/Reports.vue") },
        { path: "suggestions", component: () => import("./views/Suggestions.vue") },
        { path: "notifications", component: () => import("./views/Notifications.vue") },
        { path: "settings", component: () => import("./views/Settings.vue") },
        { path: "oss", component: () => import("./views/Oss.vue") },
      ],
    },
  ],
});

router.beforeEach((to) => {
  if (to.path !== "/login" && !getToken()) return "/login";
});
