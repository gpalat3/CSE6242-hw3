// Databricks notebook source
// STARTER CODE - DO NOT EDIT THIS CELL
import org.apache.spark.sql.functions.desc
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import spark.implicits._
import org.apache.spark.sql.expressions.Window

// COMMAND ----------

// STARTER CODE - DO NOT EDIT THIS CELL
val customSchema = StructType(Array(StructField("lpep_pickup_datetime", StringType, true), StructField("lpep_dropoff_datetime", StringType, true), StructField("PULocationID", IntegerType, true), StructField("DOLocationID", IntegerType, true), StructField("passenger_count", IntegerType, true), StructField("trip_distance", FloatType, true), StructField("fare_amount", FloatType, true), StructField("payment_type", IntegerType, true)))

// COMMAND ----------

// STARTER CODE - YOU CAN LOAD ANY FILE WITH A SIMILAR SYNTAX.
val df = spark.read
   .format("com.databricks.spark.csv")
   .option("header", "true") // Use first line of all files as header
   .option("nullValue", "null")
   .schema(customSchema)
   .load("/FileStore/tables/nyc_tripdata.csv") // the csv file which you want to work with
   .withColumn("pickup_datetime", from_unixtime(unix_timestamp(col("lpep_pickup_datetime"), "MM/dd/yyyy HH:mm")))
   .withColumn("dropoff_datetime", from_unixtime(unix_timestamp(col("lpep_dropoff_datetime"), "MM/dd/yyyy HH:mm")))
   .drop($"lpep_pickup_datetime")
   .drop($"lpep_dropoff_datetime")

// COMMAND ----------

// LOAD THE "taxi_zone_lookup.csv" FILE SIMILARLY AS ABOVE. CAST ANY COLUMN TO APPROPRIATE DATA TYPE IF NECESSARY.
// ENTER THE CODE BELOW
val taxiSchema = StructType(Array(StructField("LocationID", IntegerType, true), StructField("Borough", StringType, true), StructField("Zone", StringType, true), StructField("service_zone", StringType, true)))
val df_taxi = spark.read
   .format("com.databricks.spark.csv")
   .option("header", "true") // Use first line of all files as header
   .option("nullValue", "null")
   .schema(taxiSchema)
   .load("/FileStore/tables/taxi_zone_lookup.csv")

// COMMAND ----------

// STARTER CODE - DO NOT EDIT THIS CELL
// Some commands that you can use to see your dataframes and results of the operations. You can comment the df.show(5) and uncomment display(df) to see the data differently. You will find these two functions useful in reporting your results.
// display(df)
df.show(5) // view the first 5 rows of the dataframe

// COMMAND ----------

// STARTER CODE - DO NOT EDIT THIS CELL
// Filter the data to only keep the rows where "PULocationID" and the "DOLocationID" are different and the "trip_distance" is strictly greater than 2.0 (>2.0).

// VERY VERY IMPORTANT: ALL THE SUBSEQUENT OPERATIONS MUST BE PERFORMED ON THIS FILTERED DATA

val df_filter = df.filter($"PULocationID" =!= $"DOLocationID" && $"trip_distance" > 2.0)
df_filter.show(5)

// COMMAND ----------

// PART 1a: The top-5 most popular drop locations - "DOLocationID", sorted in descending order - if there is a tie, then one with lower "DOLocationID" gets listed first
// Output Schema: DOLocationID int, number_of_dropoffs int 

// Hint: Checkout the groupBy(), orderBy() and count() functions.

// ENTER THE CODE BELOW
val df_popular_dropoff = df.groupBy("DOLocationID")
                  .count()
                  .withColumnRenamed("count", "number_of_dropoffs")
                  .orderBy($"number_of_dropoffs".desc, $"DOLocationID")
df_popular_dropoff.show(5)

// COMMAND ----------

// PART 1b: The top-5 most popular pickup locations - "PULocationID", sorted in descending order - if there is a tie, then one with lower "PULocationID" gets listed first 
// Output Schema: PULocationID int, number_of_pickups int

// Hint: Code is very similar to part 1a above.

// ENTER THE CODE BELOW
val df_popular_pickup = df.groupBy("PULocationID")
                  .count()
                  .withColumnRenamed("count", "number_of_pickups")
                  .orderBy($"number_of_pickups".desc, $"PULocationID")
df_popular_pickup.show(5)

// COMMAND ----------

// PART 2: List the top-3 locations with the maximum overall activity, i.e. sum of all pickups and all dropoffs at that LocationID. In case of a tie, the lower LocationID gets listed first.
// Output Schema: LocationID int, number_activities int

// Hint: In order to get the result, you may need to perform a join operation between the two dataframes that you created in earlier parts (to come up with the sum of the number of pickups and dropoffs on each location). 

// ENTER THE CODE BELOW
val df_popular_locations = df_popular_pickup.join(df_popular_dropoff, df_popular_pickup("PULocationID") === df_popular_dropoff("DOLocationID"), "fullouter")
                           .withColumn("number_activities", col("number_of_pickups") + col("number_of_dropoffs"))
val df_top_locations = df_popular_locations.select("PULocationID", "number_activities")
              .withColumnRenamed("PULocationID", "LocationID")
              .orderBy($"number_activities".desc, $"LocationID")
df_top_locations.show(3)

// COMMAND ----------

// PART 3: List all the boroughs in the order of having the highest to lowest number of activities (i.e. sum of all pickups and all dropoffs at that LocationID), along with the total number of activity counts for each borough in NYC during that entire period of time.
// Output Schema: Borough string, total_number_activities int

// Hint: You can use the dataframe obtained from the previous part, and will need to do the join with the 'taxi_zone_lookup' dataframe. Also, checkout the "agg" function applied to a grouped dataframe.

// ENTER THE CODE BELOW
val df_boroughs = df_taxi.join(df_top_locations, df_top_locations("LocationID") === df_taxi("LocationID"), "inner")
val df_borough_activities = df_boroughs.groupBy("Borough")
                  .sum("number_activities")
                  .withColumnRenamed("sum(number_activities)", "total_number_activities")
                  .orderBy($"total_number_activities".desc, $"Borough")
df_borough_activities.show()

// COMMAND ----------

// PART 4: List the top 2 days of week with the largest number of (daily) average pickups, along with the values of average number of pickups on each of the two days. The day of week should be a string with its full name, for example, "Monday" - not a number 1 or "Mon" instead.
// Output Schema: day_of_week string, avg_count float

// Hint: You may need to group by the "date" (without time stamp - time in the day) first. Checkout "to_date" function.

// ENTER THE CODE BELOW
val df_popular_pickup_with_time = df_popular_pickup.join(df, df_popular_pickup("PULocationID") === df("PULocationID"), "inner")
                                  .withColumn("day_of_week", date_format(col("pickup_datetime"), "EEEE"))
// df_popular_pickup_with_time.show(10)
val df_popular_pickup_day = df_popular_pickup_with_time.groupBy("day_of_week")
                            .avg("number_of_pickups")
                            .withColumnRenamed("avg(number_of_pickups)", "avg_count")
                            .orderBy($"avg_count".desc, $"day_of_week")
df_popular_pickup_day.show(2)

// COMMAND ----------

// PART 5: For each particular hour of a day (0 to 23, 0 being midnight) - in their order from 0 to 23, find the zone in Brooklyn borough with the LARGEST number of pickups. 
// Output Schema: hour_of_day int, zone string, max_count int

// Hint: You may need to use "Window" over hour of day, along with "group by" to find the MAXIMUM count of pickups

// ENTER THE CODE BELOW
val df_borough_bkn = df_taxi.filter($"Borough" === "Brooklyn")
val df_pickup_bkn = df.join(df_borough_bkn, df_borough_bkn("LocationID") === df("PULocationID"), "inner")
                    .withColumn("hour_of_day", hour(col("pickup_datetime")))
val df_popular_pickup_bkn_hour = df_pickup_bkn.groupBy("hour_of_day", "Zone")
                                 .count()
                                 .withColumnRenamed("count", "number_of_pickups")
                                 .orderBy($"number_of_pickups".desc, $"Zone", $"hour_of_day")
val df_bkn_max_count = df_popular_pickup_bkn_hour.groupBy("hour_of_day")
                            .max("number_of_pickups")
                            .withColumnRenamed("max(number_of_pickups)", "max_count")
                            .orderBy($"hour_of_day")
// df_bkn_max_count.show(24)
val df_bkn_zone_max = df_bkn_max_count.join(df_popular_pickup_bkn_hour, df_popular_pickup_bkn_hour("hour_of_day") === df_bkn_max_count("hour_of_day") 
                                            && df_popular_pickup_bkn_hour("number_of_pickups") === df_bkn_max_count("max_count"), "inner")
                      .select(df_bkn_max_count("hour_of_day"), df_popular_pickup_bkn_hour("zone"), df_bkn_max_count("max_count"))
                      .orderBy($"hour_of_day")
df_bkn_zone_max.show(24)

// COMMAND ----------

// PART 6 - Find which 3 different days of the January, in Manhattan, saw the largest percentage increment in pickups compared to previous day, in the order from largest increment % to smallest increment %. 
// Print the day of month along with the percent CHANGE (can be negative), rounded to 2 decimal places, in number of pickups compared to previous day.
// Output Schema: day int, percent_change float


// Hint: You might need to use lag function, over a window ordered by day of month.

// ENTER THE CODE BELOW
val df_borough_man = df_taxi.filter($"Borough" === "Manhattan")
val df_pickup_man = df.join(df_borough_man, df_borough_man("LocationID") === df("PULocationID"), "inner")
                    .withColumn("day", date_format(col("pickup_datetime"), "d").cast("integer"))
                    .withColumn("month", date_format(col("pickup_datetime"), "LLL"))
val df_pickup_man_jan = df_pickup_man.filter($"month" === "Jan")
                    .groupBy("day")
                    .count()
                    .withColumnRenamed("count", "number_of_pickups")
                    .orderBy($"number_of_pickups".desc, $"day")
val window = Window.orderBy("day")
//use lag to get previous row value for salary, 1 is the offset
val lagCol = lag(col("number_of_pickups"), 1).over(window)
val df_pickup_man_jan_lag = df_pickup_man_jan.withColumn("prev_number_of_pickups", lagCol)
                            .withColumn("percent_change", ((col("number_of_pickups") - col("prev_number_of_pickups")) * 100 / col("prev_number_of_pickups")).cast("float"))
                            .orderBy($"percent_change".desc)
val df_pickup_manhattan = df_pickup_man_jan_lag.select("day", "percent_change")
df_pickup_manhattan.show(3)
