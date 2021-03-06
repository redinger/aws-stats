(ns aws-stats.analysis
  "This is where we do analysis of what we have in the database"
  (:require [datomic.api :as d]))

(defn download-equivalents
  "Returns a map of S3 URIs to the equivalent number of times it has
  been downloaded. A download equivalent is the number of bytes
  transferred divided by the object size."
  [db]
  (->> (d/q '[:find ?uri (sum ?download)
              ;; We need a :with because we don't want to sum
              ;; *unique* downloads, but *all* downloads
              :with ?request-id
              :where
              [?entry :aws-stats/bytes-sent ?bytes-sent]
              [?entry :aws-stats/object-size ?object-size]
              [?entry :aws-stats/key ?key]
              [?entry :aws-stats/entry-bucket ?bucket]
              [?entry :aws-stats/request-id ?request-id]
              [(str "s3://" ?bucket "/" ?key) ?uri]
              [(double ?bytes-sent) ?bytes-double]
              [(/ ?bytes-double ?object-size) ?download]]
            db)
       (into [])
       (sort-by second)
       reverse))


;; TODO:

(comment
 (let [stats (map parse-line (lines (log-files (first args))))
       successes (filter #(= "200" (:status %)) stats)
       by-key (group-by :key successes)
       ;; TODO: make this readable by humans (including me)
       results (reverse (sort-by second (map (fn [[k v]] (let [size (safe-decode (:object-size (first v)))
                                                               total-bytes (reduce + (map #(safe-decode (:bytes-sent %)) v))]
                                                           [k (count v) (when (not= 0 size) (float (/ total-bytes size)))]))
                                             by-key)))
       max-key-length (reduce max (map count (keys by-key)))
       fmt (str "%" max-key-length "s %10s %10s")]
   (println (format fmt "object" "requests" "equivalent downloads"))
   (doseq [result results]
     (println (apply format fmt result)))))
