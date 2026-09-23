package com.artworkguard.marketplace.aliexpress;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.math.BigDecimal;
import java.util.*;
@Component
public class AliClient {
 private final AliSettings settings;private final AliTransport transport;private final ObjectMapper mapper;private final Clock clock;
 public AliClient(AliSettings settings,AliTransport transport,ObjectMapper mapper,Clock clock){this.settings=settings;this.transport=transport;this.mapper=mapper;this.clock=clock;}
 public List<AliDtos.Listing> search(AliDtos.SearchRequest request){
  settings.requireReady();var p=new TreeMap<String,String>();
  p.put("method","aliexpress.affiliate.product.query");p.put("app_key",settings.key());p.put("sign_method","md5");
  p.put("timestamp",DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(clock.instant().atOffset(ZoneOffset.ofHours(8))));
  p.put("format","json");p.put("v","2.0");p.put("keywords",request.query().strip());p.put("page_no",Integer.toString(request.page()));p.put("page_size",Integer.toString(request.limit()));
  p.put("target_currency","USD");p.put("target_language","EN");p.put("ship_to_country","US");
  if(!settings.tracking().isBlank())p.put("tracking_id",settings.tracking());
  p.put("sign",sign(p,settings.secret()));
  String form=p.entrySet().stream().map(e->encode(e.getKey())+"="+encode(e.getValue())).collect(java.util.stream.Collectors.joining("&"));
  try{return parse(mapper.readTree(transport.post(form.getBytes(StandardCharsets.UTF_8))),request.limit());}
  catch(java.io.IOException e){throw AliException.invalid();}
 }
 public static String sign(Map<String,String> parameters,String secret){
  var raw=new StringBuilder(secret);
  new TreeMap<>(parameters).forEach((k,v)->{if(!"sign".equals(k)&&v!=null&&!v.isEmpty())raw.append(k).append(v);});
  raw.append(secret);
  try{return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("MD5").digest(raw.toString().getBytes(StandardCharsets.UTF_8)));}
  catch(Exception e){throw new IllegalStateException("Signature unavailable");}
 }
 public static List<AliDtos.Listing> parse(JsonNode root,int limit){
  if(limit<1||limit>5)throw new IllegalArgumentException("Limit must be 1..5");
  if(root==null||root.has("error_response"))throw AliException.invalid();
  var result=root.path("aliexpress_affiliate_product_query_response").path("resp_result");
  if(result.path("resp_code").asInt()!=200)throw AliException.invalid();
  var data=result.path("result");var products=data.path("products").path("product");
  if(products.isMissingNode()&&data.path("current_record_count").asInt(-1)==0)return List.of();
  if(!products.isArray()||products.size()>50)throw AliException.invalid();
  var found=new ArrayList<AliDtos.Listing>();var seen=new HashSet<String>();
  for(var node:products){
   String id=text(node,"product_id",30),shop=text(node,"shop_id",30),title=text(node,"product_title",500);
   if(!id.matches("[0-9]{1,30}")||!shop.matches("[0-9]{1,30}")||!seen.add(id))throw AliException.invalid();
   String field=node.hasNonNull("target_sale_price")?"target_sale_price":"sale_price";
   BigDecimal price;try{price=new BigDecimal(text(node,field,30));}catch(NumberFormatException e){throw AliException.invalid();}
   String currency=text(node,field+"_currency",3);
   if(price.signum()<0||price.scale()>2||price.precision()-price.scale()>10||!currency.matches("[A-Z]{3}"))throw AliException.invalid();
   String image=text(node,"product_main_image_url",2048);AliTransport.imageUri(image);
   found.add(new AliDtos.Listing(id,title,shop,price,currency,image));if(found.size()==limit)break;
  }
  return List.copyOf(found);
 }
 private static String text(JsonNode node,String field,int max){String value=node.path(field).asText("");if(value.isBlank()||value.length()>max)throw AliException.invalid();return value;}
 private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
}
