(ns dynamo.geom
  (:require [schema.macros :as sm]
            [schema.core :as s]
            [dynamo.types :as dt])
  (:import [dynamo.types Rect AABB]
           [com.dynamo.bob.textureset TextureSetGenerator]
           [javax.vecmath Point3d Point4d Vector4d Vector3d Matrix4d]))

(defn clamper [low high] (fn [x] (min (max x low) high)))

; -------------------------------------
; 2D geometry
; -------------------------------------
(sm/defn area :- double
  [r :- Rect]
  (if r
    (* (double (.width r)) (double (.height r)))
    0))

(sm/defn intersect :- (s/maybe Rect)
  ([r :- Rect] r)
  ([r1 :- Rect r2 :- Rect]
    (when (and r1 r2)
      (let [l (max (.x r1) (.x r2))
            t (max (.y r1) (.y r2))
            r (min (+ (.x r1) (.width r1))  (+ (.x r2) (.width r2)))
            b (min (+ (.y r1) (.height r1)) (+ (.y r2) (.height r2)))
            w (- r l)
            h (- b t)]
        (if (and (< 0 w) (< 0 h))
          (dt/rect l t w h)
          nil))))
  ([r1 :- Rect r2 :- Rect & rs :- [Rect]]
    (reduce intersect (intersect r1 r2) rs)))

(sm/defn split-rect :- [Rect]
  [container :- Rect content :- Rect]
  (let [new-rects (transient [])]
    (if-let [overlap (intersect container content)]
      (do
        ;; bottom slice
        (if (< (.y container) (.y overlap))
          (conj! new-rects (dt/rect (.x container)
                                 (.y container)
                                 (.width container)
                                 (- (.y overlap) (.y container)))))

        ;; top slice
        (if (< (+ (.y overlap) (.height overlap)) (+ (.y container) (.height container)))
          (conj! new-rects (dt/rect (.x container)
                                 (+ (.y overlap) (.height overlap))
                                 (.width container)
                                 (- (+ (.y container) (.height container))
                                    (+ (.y overlap)   (.height overlap))))))

        ;; left slice
        (if (< (.x container) (.x overlap))
          (conj! new-rects (dt/rect (.x container)
                                 (.y overlap)
                                 (- (.x overlap) (.x container))
                                 (.height overlap))))

        ;; right slice
        (if (< (+ (.x overlap) (.width overlap)) (+ (.x container) (.width container)))
          (conj! new-rects (dt/rect ""
                                (+ (.x overlap) (.width overlap))
                                (.y overlap)
                                (- (+ (.x container) (.width container))
                                    (+ (.x overlap)   (.width overlap)))
                                (.height overlap)))))
      (conj! new-rects container))
    (persistent! new-rects)))

; This is off-by-one in many cases, due to Clojure's preference to promote things into Double and Long.
;
;(defn to-short-uv
;  "Return a fixed-point integer representation of the fractional part of the given fuv."
;  [^Float fuv]
;  (.shortValue
;    (bit-and
;      (int
;        (* (float fuv) (.floatValue 65535.0)))
;      0xffff)))

(defn to-short-uv
  [^Float fuv]
  (TextureSetGenerator/toShortUV fuv))

; -------------------------------------
; Transformations
; -------------------------------------

(sm/defn world-space [node :- {:world-transform Matrix4d s/Any s/Any} point :- Point3d]
  (let [p (Point3d. point)]
    (.transform (:world-transform node) p)
    p))

(def Identity4d (doto (Matrix4d.) (.setIdentity)))

; -------------------------------------
; Matrix sloshing
; -------------------------------------
(defmulti as-array class)
(defmulti invert class)

(defmethod as-array Matrix4d
  [x]
  (float-array [(.m00 x) (.m10 x) (.m20 x) (.m30 x)
                (.m01 x) (.m11 x) (.m21 x) (.m31 x)
                (.m02 x) (.m12 x) (.m22 x) (.m32 x)
                (.m03 x) (.m13 x) (.m23 x) (.m33 x)]))

(defmethod as-array Vector3d
  [v]
  (let [vals (double-array 3)]
    (.get v vals)
    vals))

(defmethod as-array Point3d
  [v]
  (let [vals (double-array 3)]
    (.get v vals)
    vals))

(defmethod as-array Vector4d
  [v]
  (let [vals (double-array 4)]
    (.get v vals)
    vals))

(defmethod invert Matrix4d
  [m]
  (doto (Matrix4d. m)
    .invert))

(sm/defn ident :- Matrix4d
  []
  (doto (Matrix4d.)
    (.setIdentity)))

; -------------------------------------
; 3D geometry
; -------------------------------------
(sm/defn null-aabb :- AABB
  []
  (dt/->AABB (Point3d. Integer/MAX_VALUE Integer/MAX_VALUE Integer/MAX_VALUE)
             (Point3d. Integer/MIN_VALUE Integer/MIN_VALUE Integer/MIN_VALUE)))

(sm/defn aabb-incorporate :- AABB
  ([aabb :- AABB p :- Point3d]
    (aabb-incorporate aabb (.x p) (.y p) (.z p)))
  ([aabb :- AABB x :- s/Num y :- s/Num z :- s/Num]
    (let [minx (Math/min (.. aabb min x) x)
          miny (Math/min (.. aabb min y) y)
          minz (Math/min (.. aabb min z) z)
          maxx (Math/max (.. aabb max x) x)
          maxy (Math/max (.. aabb max y) y)
          maxz (Math/max (.. aabb max z) z)]
      (dt/->AABB (Point3d. minx miny minz) (Point3d. maxx maxy maxz)))))

(sm/defn aabb-union :- AABB
  ([aabb1 :- AABB] aabb1)
  ([aabb1 :- AABB aabb2 :- AABB]
    (-> aabb1
      (aabb-incorporate (.min aabb2))
      (aabb-incorporate (.max aabb2))))
  ([aabb1 :- AABB aabb2 :- AABB & aabbs :- [AABB]] (aabb-union (aabb-union aabb1 aabb2) aabbs)))

(sm/defn aabb-contains?
  [aabb :- AABB p :- Point3d]
  (and
    (>= (.. aabb max x) (.x p) (.. aabb min x))
    (>= (.. aabb max y) (.y p) (.. aabb min y))
    (>= (.. aabb max z) (.z p) (.. aabb min z))))

(sm/defn aabb-extent :- Point3d
  [aabb :- AABB]
  (let [v (Point3d. (.max aabb))]
    (.sub v (.min aabb))
    v))

(sm/defn aabb-center :- Point3d
  [aabb :- AABB]
  (Point3d. (/ (+ (.. aabb min x) (.. aabb max x)) 2.0)
            (/ (+ (.. aabb min y) (.. aabb max y)) 2.0)
            (/ (+ (.. aabb min z) (.. aabb max z)) 2.0)))
