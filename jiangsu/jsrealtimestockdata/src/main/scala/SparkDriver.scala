import java.text.SimpleDateFormat
import java.util.regex.{Matcher, Pattern}
import java.util.{Calendar, Date}

import com.kunyan.telecom.{KafkaConf, KafkaProducer, SUELogger}
import org.apache.spark.SparkConf
import org.apache.spark.streaming.{Seconds, StreamingContext}

/**
  * Created by C.J.YOU on 2016/8/13.
  */
object SparkDriver {

  val EXPIRE_DAY = "1"

  /**
    * 往kafka发送消息
    *
    * @param table kv表名
    * @param key kv的key
    * @param value kv的返回value值
    */

  def sendToKafka(table: String, key: String, value: String): Unit = {

    KafkaProducer.send(table + "\001" + EXPIRE_DAY + "\001" + key + "\001" + value)

  }

  // get time : 20150407172230, 年月日时分,秒取整
  def getCurrentTime: String = {

    val format = new SimpleDateFormat("yyyyMMddHHmm")
    val cal = Calendar.getInstance()
    cal.setTime(new Date())
    val time = format.format(cal.getTime)
    val currentMinute = cal.get(Calendar.SECOND)
    val mod = if(currentMinute % 10 < 5) 0 else 5
    val min = (currentMinute / 10) * 10 + mod

    time + min

  }

  /**
    * 搜索数据的匹配
    * 字段格式如下：
    * ad:string
    * ts:long
    * host:string
    * uri:string
    * referer:string
    * srcip:string
    * dstip:string
    * ua:string
    * cookie:string
    * accept:string
    * src_port:int
    *
    * @param input 源数据
    * @return 匹配后的数据
    */
  def VisitAndSearch(input: String): String = {

    val visitSearchPatterns = Array[String](
      // 搜索
      "quotes.money.163.com.*word=(.*)&t=", "quotes.money.163.com.*word=(.*)&count",
      "suggest3.sinajs.cn.*key=(((?!&name).)*)", "xueqiu.com.*code=(.*)&size", "xueqiu.com.*q=(.*)",
      "app.stcn.com.*&wd=(.*)", "q.ssajax.cn.*q=(.*)&type", "znsv.baidu.com.*wd=(.*)&ch",
      "so.stockstar.com.*q=(.*)&click", "quote.cfi.cn.*keyword=(.*)&his", "hq.cnstock.com.*q=(.*)&limit",
      "xinpi.cs.com.cn.*q=(.*)", "xinpi.cs.com.cn.*c=(.*)&q", "search.cs.com.cn.*searchword=(.*)",
      "d.10jqka.com.cn.*(\\d{6})/last", "news.10jqka.com.cn.*keyboard_(\\d{6})",
      "10jqka.com.cn.*w=(.*)&tid", "data.10jqka.com.cn.*/(\\d{6})/", "dict.hexin.cn.*pattern=(\\d{6})",
      "dict.hexin.cn.*pattern=(\\S.*)", "q.stock.sohu.com.*keyword=(.*)&(_|c)",
      "api.k.sohu.com.*words=(.*)&p1", "hq.p5w.net.*query=(.*)&i=", "code.jrjimg.cn.*key=(.*)&d=",
      "itougu.jrj.com.cn.*keyword=(.*)&is_stock", "wallstreetcn.com/search\\?q=(.*)",
      "api.markets.wallstreetcn.com.*q=(.*)&limit", "so.hexun.com.*key=(.*)&type",
      "finance.ifeng.com.*code.*(\\d{6})", "finance.ifeng.com.*q=(.*)&cb",
      "suggest.eastmoney.com.*input=(.*?)&", "so.eastmoney.com.*q=(.*)&m",
      "guba.eastmoney.com/search.aspx?t=(.*)", "www.yicai.com.*searchKeyWords=(.*)",
      "www.gw.com.cn.*q=(.*)&s=", "cailianpress.com/search.*keyword=(.*)",
      "api.cailianpress.com.*PutinInfo=(.*)&sign", "news.21cn.com.*keywords=(.*)&view",
      "news.10jqka.com.cn.*text=(.*)&jsoncallback", "smartbox.gtimg.cn.*q=(.*?)&",
      "swww.niuguwang.com/stock/.*q=(.*?)&","info.zq88.cn:9085.*query=(\\d{6})&",
      // 查看
      "quotes.money.163.com/app/stock/\\d(\\d{6})", "m.news.so.com.*q=(\\d{6})",
      "finance.sina.com.cn.*(\\d{6})/nc.shtml", "guba.sina.com.cn.*name=.*(\\d{6})",
      "platform.*symbol=\\w\\w(\\d{6})", "xueqiu.com/S/.*(\\d{6})", "cy.stcn.com/S/.*(\\d{6})",
      "mobile.stcn.com.*secucode=(\\d{6})", "stock.quote.stockstar.com/(\\d{6}).shtml",
      "quote.cfi.cn.*searchcode=(.*)", "hqapi.gxfin.com.*code=(\\d{6}).sh",
      "irm.cnstock.com.*index/(\\d{6})", "app.cnstock.com.*k=(\\d{6})",
      "guba.eastmoney.com.*code=.*(\\d{6})", "stockpage.10jqka.com.cn/(\\d{6})/",
      "0033.*list.*(\\d{6}).*json", "q.stock.sohu.com/cn/(\\d{6})/", "s.m.sohu.com.*/(\\d{6})",
      "data.p5w.net.*code.*(\\d{6})", "hq.p5w.net.*a=.*(\\d{6})", "jrj.com.cn.*(\\d{6}).s?html",
      "mapi.jrj.com.cn.*stockcode=(\\d{6})", "api.buzz.wallstreetcn.com.*(\\d{6})&cid",
      "stockdata.stock.hexun.com/(\\d{6}).shtml", "finance.ifeng.com/app/hq/stock.*(\\d{6})",
      "api.3g.ifeng.com.*k=(\\d{6})", "quote.*(\\d{6}).html", "gw.*stock.*?(\\d{6})",
      "tfile.*(\\d{6})/fs_remind", "api.cailianpress.com.*(\\d{6})&Sing",
      "stock.caijing.com.cn.*(\\d{6}).html", "gu.qq.com.*(\\d{6})\\?", "gubaapi.*code=.*(\\d{6})",
      "mnews.gw.com.cn.*(\\d{6})/(list|gsgg|gsxw|yjbg|trader|ipad2_gg)", "101.226.68.82.*code=.*(\\d{6})",
      "183.238.123.235.*code=(\\d{6})&", "zszx.newone.com.cn.*code=(\\d{6})",
      "compinfo.hsmdb.com.*stock_code=(\\d{6})", "mt.emoney.cn.*barid=(\\d{6})",
      "news.10jqka.com.cn/stock_mlist/(\\d{6})","www.newone.com.cn.*code=(\\d{6})",
      "219.141.183.57.*gpdm=(\\d{6})&","www.hczq.com.*stockCode=(\\d{6})",
      "open.hs.net.*en_prod_code=(\\d{6})","stock.pingan.com.cn.*secucodes=(\\d{6})",
      "58.63.254.170:7710.*code=(\\d{6})&","sjf10.westsecu.com/stock/(\\d{6})/",
      "mds.gf.com.cn.*/(\\d{6})","211.152.53.105.*key=(\\d{6}),")

    val data = input.split("\t",-1)

    if (data.length > 5) {

      val url = data(2) + data(3)

      var i: Int = 0

      var stockCode: String = ""

      var time: String = ""

      for (i <- 1 to visitSearchPatterns.length) {

        val matcher: Matcher = Pattern.compile(visitSearchPatterns(i - 1)).matcher(url)

        if (matcher.find) {

          stockCode = matcher.group(1)
          time = data(1) + "\t" + String.valueOf(i)

        }
      }
      if (!"".equals(stockCode) && stockCode.length <= 6000) {
        stockCode + "\t" + time
      } else {
        null
      }

    } else {
      null
    }

  }

  def main(args: Array[String]): Unit = {

    /*System.setProperty("spark.shuffle.consolidateFiles", "true")
    System.setProperty("spark.speculation", "true")
    System.setProperty("spark.streaming.concurrentJobs", "10")
    System.setProperty("spark.yarn.max.executor.failures", "99")
    System.setProperty("spark.streaming.blockInterval", "100")
    System.setProperty("spark.akka.timeout", "300")
    System.setProperty("spark.rpc.numRetries", "6")
    System.setProperty("spark.executor.heartbeatInterval", "20")
    System.setProperty("spark.task.maxFailures", "8")
    System.setProperty("spark.network.timeout", "1000")
    System.setProperty("spark.driver.allowMultipleContexts","true")*/

    if(args.length < 2) {
      sys.error("Usage: <kv_table> <group_id> <sendTopic>")
    }

    val Array(table, groupID) = args

    val conf  = new SparkConf()

    val ssc = new StreamingContext(conf, Seconds(5))

    val topicMap = KafkaConf.TOPIC.split(",").map((_, KafkaConf.NUMBER_THREAD.toInt)).toMap

    val numStrems = 5

    val kafkaStreams =(1 to numStrems).map{ i=> KafkaConf.createStream(ssc, KafkaConf.ZOOKEEPER_QUORUM, groupID, topicMap).map(_._2)}

    val linesData = ssc.union(kafkaStreams)

    val linesRePartition = linesData   //.persist(StorageLevel.MEMORY_AND_DISK).repartition(30)

    try {
      // 搜索数据处理
      linesRePartition.map(VisitAndSearch).filter(_ != null).foreachRDD { rdd =>
        val ts = getCurrentTime
        val res = rdd.zipWithIndex()
        res.saveAsTextFile("hdfs://ns1/user/sparkuser/private/kunyan/data/send_" + ts)
        res.foreach(record => sendToKafka(table, ts + "_ky_" + record._2, record._1))
      }

    } catch {
      case e: Exception =>
        SUELogger.error("mapPartition error!!!!")
    }

    ssc.start()
    ssc.awaitTermination()
  }

}
