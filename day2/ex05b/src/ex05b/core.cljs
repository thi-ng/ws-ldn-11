(ns ex05b.core
  (:require-macros
   [reagent.ratom :refer [reaction]]
   [cljs-log.core :refer [debug info warn]])
  (:require
   [thi.ng.math.core :as m :refer [PI HALF_PI TWO_PI]]
   [thi.ng.color.core :as col]
   [thi.ng.geom.core :as g]
   [thi.ng.geom.vector :as v :refer [vec2 vec3]]
   [thi.ng.geom.matrix :as mat :refer [M44]]
   [thi.ng.geom.gl.core :as gl]
   [thi.ng.geom.gl.webgl.constants :as glc]
   [thi.ng.geom.gl.webgl.animator :as anim]
   [thi.ng.geom.gl.shaders :as sh]
   [thi.ng.geom.gl.glmesh :as glm]
   [thi.ng.geom.gl.shaders.phong :as phong]
   [thi.ng.glsl.core :as glsl :include-macros true]
   [reagent.core :as reagent]))

(defonce app (reagent/atom {}))

(def meshes
  ["../assets/suzanne.stl"
   "../assets/deadpool.stl"
   "../assets/voxel.stl"])

(defn trigger-mesh-change!
  [uri]
  (swap! app assoc :selected-mesh uri)
  (.postMessage (:worker @app) uri))

(defn receive-mesh!
  [msg]
  (let [[vertices fnormals id tx] (.-data msg)
        num   (* id 9)
        mesh  (thi.ng.geom.gl.glmesh.GLMesh.
               (js/Float32Array. vertices 0 num)
               (js/Float32Array. fnormals 0 num)
               nil nil nil id
               #{:fnorm})
        model (-> mesh
                  (gl/as-gl-buffer-spec {})
                  (assoc :shader   (:shader @app)
                         :uniforms (assoc (:uniforms @app) :model tx))
                  (gl/make-buffers-in-spec (:gl @app) glc/static-draw))]
    (swap! app assoc
           :mesh     mesh
           :model    model)))

(defn init-gl
  [this]
  (let [gl       (gl/gl-context (reagent/dom-node this))
        vport    (gl/get-viewport-rect gl)
        shader   (sh/make-shader-from-spec gl phong/shader-spec)
        uniforms {:model         M44
                  :proj          (mat/perspective 60 vport 0.1 10)
                  :view          (mat/look-at (v/vec3 0 0 1.25) (v/vec3) v/V3Y)
                  :lightPos      (vec3 0.1 0 1)
                  :ambientCol    0x000011
                  :diffuseCol    0x0033ff
                  :specularCol   0xffffff
                  :shininess     100
                  :wrap          0
                  :useBlinnPhong true}]
    (swap! app assoc
           :gl       gl
           :viewport vport
           :shader   shader
           :uniforms uniforms)))

(defn update-gl
  [this]
  (fn [t frame]
    (let [{:keys [gl viewport model model-tx]} @app]
      (when model
        (doto gl
          (gl/set-viewport viewport)
          (gl/clear-color-and-depth-buffer (col/rgba 0.3 0.3 0.3) 1)
          (gl/draw-with-shader
           (-> model
               (update-in [:uniforms :model] #(m/* (g/rotate-y M44 t) %)))))))
    (:active (reagent/state this))))

(defn gl-component
  [props]
  (reagent/create-class
   {:component-did-mount
    (fn [this]
      (reagent/set-state this {:active true})
      ((:init props) this)
      (anim/animate ((:loop props) this)))
    :component-will-unmount
    (fn [this]
      (debug "unmount GL")
      (reagent/set-state this {:active false}))
    :reagent-render
    (fn [_]
      [:canvas
       (merge
        {:width (.-innerWidth js/window)
         :height (.-innerHeight js/window)}
        props)])}))

(defn mesh-selecta
  []
  (let [sel (reaction (:selected-mesh @app))]
    (fn []
      [:select
       {:default-value @sel
        :on-change     #(trigger-mesh-change! (-> % .-target .-value))}
       [:option "Choose:"]
       (for [o meshes] [:option {:key o :value o} o])])))

(defn app-component
  []
  [:div
   [:div [mesh-selecta]]
   [gl-component
    {:init          init-gl
     :loop          update-gl
     ;;:on-mouse-move (fn [e] (game/update-player-pos! (.-clientX e)))
     ;;:on-mouse-down (fn [e] (game/player-max-speed!))
     ;;:on-mouse-up   (fn [e] (game/player-normal-speed!))
     ;;:on-touch-move (fn [e]
     ;;                 (game/update-player-pos! (.-clientX (aget (.-touches e) 0)))
     ;;                 (game/player-max-speed!))
     ;;:on-touch-end  (fn [e] (game/player-normal-speed!))
     }]])

(defn main
  []
  (.initializeTouchEvents js/React)
  (let [worker (js/Worker. "js/meshworker.js")]
    (set! (.-onmessage worker) receive-mesh!)
    (swap! app assoc :worker worker)
    (reagent/render-component
     [app-component]
     (.getElementById js/document "app"))))

(main)
