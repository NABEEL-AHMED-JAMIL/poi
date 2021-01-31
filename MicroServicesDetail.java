package org.quorum.service.imp;


import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.google.common.reflect.TypeToken;
import com.google.gson.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quorum.domain.dto.*;
import org.quorum.domain.properties.ApiURL;
import org.quorum.domain.properties.AwsProperties;
import org.quorum.entity.base.ApiCode;
import org.quorum.entity.base.ResponseDTO;
import org.quorum.entity.domain.*;
import org.quorum.entity.dto.*;
import org.quorum.entity.dto.BulkDTO.UserPermissionsDTO;
import org.quorum.entity.enums.Status;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;


@Service
@Scope("prototype")
public class MicroServicesDetail {

    public Logger logger = LogManager.getLogger(MicroServicesDetail.class);

    @Autowired
    private Gson gson;
    @Autowired
    private ApiURL apiURL;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private AwsProperties awsProperties;
    @Autowired
    private AmazonS3 amazonS3;

    private String FEATURES = "features";
    private String GEOMETRY = "geometry";
    private String COORDINATES = "coordinates";
    private ResponseDTO responseDTO;
    private Object bodyNull = null;
    private CustomUserDetailsDTO customUserDetailsDTO;
    private AgencyAdvertiser advertiser;
    private AppUser appUser;
    private SegmentDtoBulk segmentDtoBulk;
    private PoiSegmentDetailDto poiSegmentDetailDto;
    private CreativeDetailDto creativeDetailDto;
    private LineItemsDetailDto lineItemsDetailDto;
    private Campaign campaign;
    private List<SellersDTO> sellers;
    private List<PoiDTO> poiDTOS;
    private List<AgencyAdvertiserDTO> agencyAdvertiserList;
    private List<CampaignDTO> campaignDTOList;
    private List<CreativesDTO> creativesDTOList;
    private List<LineItemDTO> lineItemDTOList;
    private LineItem lineItem;
    private CreativesDTO creativesDTO;
    private AdvertiserGroup advertiserGroup;
    private BulkSegmentDetailDto bulkSegmentDetailDto;
    private SellerMemberIdDetailDto sellerMemberIdDetailDto;

    public CustomUserDetailsDTO getCurrentLoginUser(String token) throws Exception {
        this.responseDTO = this.apiCaller(bodyNull, HttpMethod.GET,this.apiURL.getCurrentLoginUser(),this.getHeaders(token)).getBody();
        if (this.responseDTO.getCode().equals(ApiCode.SUCCESS)) {
            String json = this.gson.toJson(this.responseDTO.getContent(), LinkedHashMap.class);
            this.responseDTO = this.gson.fromJson(json, ResponseDTO.class);
            json = this.gson.toJson(this.responseDTO.getContent(), LinkedHashMap.class);
            logger.info("Json Response :- " + json);
            this.gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").setDateFormat("dd/MM/yyyy").create();
            this.customUserDetailsDTO = this.gson.fromJson(json, CustomUserDetailsDTO.class);
        }
        return this.customUserDetailsDTO;
    }

    public AppUser findAppUser(String token) throws Exception {
        this.responseDTO = this.apiCaller(bodyNull, HttpMethod.GET,this.apiURL.getAppUser(),this.getHeaders(token)).getBody();
        if (this.responseDTO.getCode().equals(ApiCode.SUCCESS)) {
            String json = this.gson.toJson(this.responseDTO.getContent(), LinkedHashMap.class);
            logger.info("Json Response :- " + json);
            this.appUser = this.gson.fromJson(json, AppUser.class);
        }
        return this.appUser;
    }

    public List<PoiDTO> getPois(String token, String isAgAdv, Long id) throws Exception {
        this.responseDTO = this.apiCaller(bodyNull, HttpMethod.GET,String.format(this.apiURL.getPois(), isAgAdv, id),this.getHeaders(token)).getBody();
        if(this.responseDTO.getCode().equals(ApiCode.SUCCESS)) {
            Type listOfObject = new TypeToken<List<PoiDTO>>(){}.getType();
            this.gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").setDateFormat("dd/MM/yyyy").create();
            String json = this.gson.toJson(this.responseDTO.getContent(), listOfObject);
            logger.info("Json Response :- " + json);
            this.poiDTOS = this.gson.fromJson(json, listOfObject);
        }
        return this.poiDTOS;
    }

    public List<SellersDTO> getSellers(String token) throws Exception {
        this.responseDTO = this.apiCaller(bodyNull, HttpMethod.GET,this.apiURL.getSellers(),this.getHeaders(token)).getBody();
        if(this.responseDTO.getCode().equals(ApiCode.SUCCESS)) {
            Type listOfObject = new TypeToken<List<SellersDTO>>(){}.getType();
            String json = this.gson.toJson(this.responseDTO.getContent(), listOfObject);
            logger.info("Json Response :- " + json);
            this.sellers = this.gson.fromJson(json, listOfObject);
            // sort-ing process
            this.sellers.stream().sorted(Comparator.comparingInt(SellersDTO::getId)).collect(Collectors.toList());
        }
        return this.sellers;
    }

    // working
    public List<AgencyAdvertiserDTO> getAgencyAdvertiser(String token) throws Exception {
        this.responseDTO = this.apiCaller(bodyNull, HttpMethod.GET,this.apiURL.getAdvertisers(),this.getHeaders(token)).getBody();
        if(this.responseDTO.getCode().equals(ApiCode.SUCCESS)) {
            String json = this.gson.toJson(this.responseDTO.getContent(), LinkedHashMap.class);
            logger.info("Json Response :- " + json);
            AdvertiserDTO advertiser = this.gson.fromJson(json, AdvertiserDTO.class);
            if(advertiser != null && (advertiser.getAgency() != null && advertiser.getAgency().size() > 0)) {
                this.agencyAdvertiserList = advertiser.getAgency().stream().sorted(Comparator.comparingLong(AgencyAdvertiserDTO::getId)).collect(Collectors.toList());
            }
        }
        return this.agencyAdvertiserList;
    }

    public UserPermissionsDTO getCurrentUserPermissions(String token) throws Exception {
        try {
            this.responseDTO = this.apiCaller(bodyNull, HttpMethod.GET, this.apiURL.getUserPermissions(), this.getHeaders(token)).getBody();
            UserPermissionsDTO advertiser = null;
            if (this.responseDTO.getCode().equals(ApiCode.SUCCESS)) {
                String json = this.gson.toJson(this.responseDTO.getContent(), LinkedHashMap.class);
                logger.info("Json Response :- " + json);
                advertiser = this.gson.fromJson(json, UserPermissionsDTO.class);
            }
            return advertiser;
        } catch (Exception ex) {
            return null;
        }
    }

    public AgencyAdvertiser findByIdAndAgencyId(String token, String agencyAdverId) throws Exception {
        this.responseDTO = this.apiCaller(bodyNull, HttpMethod.GET,String.format(this.apiURL.getFindByIdAndAgencyId(), agencyAdverId),this.getHeaders(token)).getBody();
        if(this.responseDTO.getCode().equals(ApiCode.SUCCESS)) {
            String json = this.gson.toJson(this.responseDTO.getContent(), LinkedHashMap.class);
            logger.info("Json Response :- " + json);
            this.advertiser = this.gson.fromJson(json, AgencyAdvertiser.class);
        }
        return this.advertiser;
    }

    public SegmentDtoBulk getSegmentsFindByIdAndStatusNot(String token, String id, Status status) throws Exception {
        this.responseDTO = this.apiCaller(bodyNull, HttpMethod.GET,String.format(this.apiURL.getSegmentsFindByIdAndStatusNot(), id, status),this.getHeaders(token)).getBody();
        if(this.responseDTO.getCode().equals(ApiCode.SUCCESS)) {
            String json = this.gson.toJson(this.responseDTO.getContent(), LinkedHashMap.class);
            logger.info("Json Response :- " + json);
            this.segmentDtoBulk = this.gson.fromJson(json, SegmentDtoBulk.class);
        }
        return this.segmentDtoBulk;
    }

    public PoiSegmentDetailDto getAudiencePoiFindByIdAndStatusNot(String token, String id, String poisIds) throws Exception {
        this.responseDTO = this.apiCaller(bodyNull, HttpMethod.GET,String.format(this.apiURL.getAudiencePoiFindByIdAndStatusNot(), id, poisIds),this.getHeaders(token)).getBody();
        if(this.responseDTO.getCode().equals(ApiCode.SUCCESS)) {
            String json = this.gson.toJson(this.responseDTO.getContent(), LinkedHashMap.class);
            logger.info("Json Response :- " + json);
            this.poiSegmentDetailDto = this.gson.fromJson(json, PoiSegmentDetailDto.class);
        }
        return this.poiSegmentDetailDto;
    }

    public PoiSegmentDetailDto getAudiencePoiGroupFindByIdAndStatusNot(String token, String id, String groupIds, Long typeId) throws Exception {
        this.responseDTO = this.apiCaller(bodyNull, HttpMethod.GET,String.format(this.apiURL.getAudiencePoiGroupFindByIdAndStatusNot(), id, groupIds, typeId),this.getHeaders(token)).getBody();
        if(this.responseDTO.getCode().equals(ApiCode.SUCCESS)) {
            String json = this.gson.toJson(this.responseDTO.getContent(), LinkedHashMap.class);
            logger.info("Json Response :- " + json);
            this.poiSegmentDetailDto = this.gson.fromJson(json, PoiSegmentDetailDto.class);
        }
        return this.poiSegmentDetailDto;
    }

    public LineItemsDetailDto getAudienceLineItemsFindByIdAndStatusNot(String token, String id, String lineItemsId) throws Exception {
        this.responseDTO = this.apiCaller(bodyNull, HttpMethod.GET,String.format(this.apiURL.getAudienceLineItemsFindByIdAndStatusNot(), id, lineItemsId),this.getHeaders(token)).getBody();
        if(this.responseDTO.getCode().equals(ApiCode.SUCCESS)) {
            String json = this.gson.toJson(this.responseDTO.getContent(), LinkedHashMap.class);
            logger.info("Json Response :- " + json);
            this.lineItemsDetailDto = this.gson.fromJson(json, LineItemsDetailDto.class);
        }
        return this.lineItemsDetailDto;
    }

    public CreativeDetailDto getAudienceCreativeDetailFindByIdAndStatusNot(String token, String id, String creativeIds) {
        this.responseDTO = this.apiCaller(bodyNull, HttpMethod.GET,String.format(this.apiURL.getAudienceCreativeDetailFindByIdAndStatusNot(), id, creativeIds),this.getHeaders(token)).getBody();
        if(this.responseDTO.getCode().equals(ApiCode.SUCCESS)) {
            String json = this.gson.toJson(this.responseDTO.getContent(), LinkedHashMap.class);
            logger.info("Json Response :- " + json);
            this.creativeDetailDto = this.gson.fromJson(json, CreativeDetailDto.class);
        }
        return this.creativeDetailDto;

    }

    public Campaign getCampFindByIdAndStatusNot(String token, String id, Status status) {
        this.responseDTO = this.apiCaller(bodyNull, HttpMethod.GET,String.format(this.apiURL.getCampFindByIdAndStatusNot(), id, status),this.getHeaders(token)).getBody();
        if(this.responseDTO.getCode().equals(ApiCode.SUCCESS)) {
            String json = this.gson.toJson(this.responseDTO.getContent(), LinkedHashMap.class);
            logger.info("Json Response :- " + json);
            this.campaign = this.gson.fromJson(json, Campaign.class);
        }
        return campaign;
    }

    public ResponseDTO saveAdvertisersDetail(String token, AgencyAdvertiserDTO agencyAdvertiserDTO, Boolean isEdit) throws Exception {
        if (isEdit) {
            this.responseDTO = this.apiCaller(agencyAdvertiserDTO, HttpMethod.PUT, this.apiURL.updateAdvertiser(),this.getHeaders(token)).getBody();
        } else {
            this.responseDTO = this.apiCaller(agencyAdvertiserDTO, HttpMethod.POST, this.apiURL.saveAdvertiser(),this.getHeaders(token)).getBody();
        }
        return this.responseDTO;
    }

    public ResponseDTO saveSegment(String token, BillboardDetailsDTO billboardDetailsDTO) throws Exception {
        return this.responseDTO = this.apiCaller(billboardDetailsDTO, HttpMethod.POST, this.apiURL.saveSegments(),this.getHeaders(token)).getBody();
    }

    public ResponseDTO savePoiAndTarget(String token, String pois, String targetSegment) throws Exception {
        return this.responseDTO = this.apiCaller(bodyNull, HttpMethod.POST, String.format(this.apiURL.savePoiAndTarget(), pois, targetSegment),this.getHeaders(token)).getBody();
    }

    public ResponseDTO savePoi(String token, PoiDTO poiDTO) throws Exception {
        return this.responseDTO = this.apiCaller(poiDTO, HttpMethod.POST, this.apiURL.savePois(),this.getHeaders(token)).getBody();
    }

    public BulkSegmentDetailDto getAllSegment(String token, String isAgAdv, Long id) throws Exception {
        this.responseDTO = this.apiCaller(bodyNull, HttpMethod.GET,String.format(this.apiURL.getAllSegment(), isAgAdv, id),this.getHeaders(token)).getBody();
        if(this.responseDTO.getCode().equals(ApiCode.SUCCESS)) {
            String json = this.gson.toJson(this.responseDTO.getContent(), LinkedHashMap.class);
            logger.info("Json Response :- " + json);
            this.bulkSegmentDetailDto = this.gson.fromJson(json, BulkSegmentDetailDto.class);
        }
        return this.bulkSegmentDetailDto;
    }

    public LineItem campLanFindByIdAndStatusNot(String token, String id, Status status) throws Exception {
        this.responseDTO = this.apiCaller(bodyNull, HttpMethod.GET,String.format(this.apiURL.getLanFindByIdAndStatusNot(), id, status),this.getHeaders(token)).getBody();
        if(this.responseDTO.getCode().equals(ApiCode.SUCCESS)) {
            String json = this.gson.toJson(this.responseDTO.getContent(), LinkedHashMap.class);
            logger.info("Json Response :- " + json);
            this.lineItem = this.gson.fromJson(json, LineItem.class);
        }
        return this.lineItem;
    }

    public SellerMemberIdDetailDto getSellersFindBySellerMemberIdBulk(String token, String ids) {
        this.responseDTO = this.apiCaller(bodyNull, HttpMethod.GET,String.format(this.apiURL.getSellersFindBySellerMemberIdBulk(), ids),this.getHeaders(token)).getBody();
        if(this.responseDTO.getCode().equals(ApiCode.SUCCESS)) {
            String json = this.gson.toJson(this.responseDTO.getContent(), LinkedHashMap.class);
            logger.info("Json Response :- " + json);
            this.sellerMemberIdDetailDto = this.gson.fromJson(json, SellerMemberIdDetailDto.class);
        }
        return this.sellerMemberIdDetailDto;
    }


    public ResponseDTO saveCampaign(String token, CampaignDTO campaignDTO) throws Exception {
        return this.responseDTO = this.apiCaller(campaignDTO, HttpMethod.POST, this.apiURL.saveCampaigns(),this.getHeaders(token)).getBody();
    }

    public ResponseDTO saveLineItems(String token, LineItemDTO lineItemDTO) throws Exception {
        return this.responseDTO = this.apiCaller(lineItemDTO, HttpMethod.POST, this.apiURL.saveLineItem(),this.getHeaders(token)).getBody();
    }

    public ResponseDTO saveCreative(String token, CreativesDTO creativesDTO, Boolean isUpdated) throws Exception {
        if (isUpdated) {
            return this.responseDTO = this.apiCaller(creativesDTO, HttpMethod.PUT, this.apiURL.updateCreatives(),this.getHeaders(token)).getBody();
        } else {
            return this.responseDTO = this.apiCaller(creativesDTO, HttpMethod.POST, this.apiURL.saveCreatives(),this.getHeaders(token)).getBody();
        }
    }

    public List<CampaignDTO> getAllCampaign(String token, String isAgAdv, Long id) throws Exception {
        this.responseDTO = this.apiCaller(bodyNull, HttpMethod.GET,String.format(this.apiURL.getCampaigns(), isAgAdv, id),this.getHeaders(token)).getBody();
        if(this.responseDTO.getCode().equals(ApiCode.SUCCESS)) {
            Type listOfObject = new TypeToken<List<CampaignDTO>>(){}.getType();
            String json = this.gson.toJson(this.responseDTO.getContent(), listOfObject);
            logger.info("Json Response :- " + json);
            this.campaignDTOList = this.gson.fromJson(json, listOfObject);
        }
        return this.campaignDTOList;
    }

    public CreativesDTO creativeFindByIdAndStatusNot(String token,String id, String agencyAdvertiserId, Status status) throws Exception {
        this.responseDTO = this.apiCaller(bodyNull, HttpMethod.GET,String.format(this.apiURL.getCreativeFindByIdAndStatusNot(), id, agencyAdvertiserId, status),this.getHeaders(token)).getBody();
        if(this.responseDTO.getCode().equals(ApiCode.SUCCESS)) {
            String json = this.gson.toJson(this.responseDTO.getContent(), LinkedHashMap.class);
            logger.info("Json Response :- " + json);
            this.creativesDTO = this.gson.fromJson(json, CreativesDTO.class);
        }
        return this.creativesDTO;
    }

    public AdvertiserGroup getGroupFindByIdAndStatusNot(String token, String id, Status status) throws Exception {
        this.responseDTO = this.apiCaller(bodyNull, HttpMethod.GET,String.format(this.apiURL.getGroupFindByIdAndStatusNot(), id, status),this.getHeaders(token)).getBody();
        if(this.responseDTO.getCode().equals(ApiCode.SUCCESS)) {
            String json = this.gson.toJson(this.responseDTO.getContent(), LinkedHashMap.class);
            logger.info("Json Response :- " + json);
            this.advertiserGroup = this.gson.fromJson(json, AdvertiserGroup.class);
        }
        return this.advertiserGroup;
    }

    public List<CreativesDTO> getCreatives(String token, Long id) throws Exception {
        this.responseDTO = this.apiCaller(bodyNull, HttpMethod.GET,String.format(this.apiURL.getCreatives(), id),this.getHeaders(token)).getBody();
        if(this.responseDTO.getCode().equals(ApiCode.SUCCESS)) {
            Type listOfObject = new TypeToken<List<CreativesDTO>>(){}.getType();
            this.gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").setDateFormat("dd/MM/yyyy").create();
            String json = this.gson.toJson(this.responseDTO.getContent(), listOfObject);
            logger.info("Json Response :- " + json);
            this.creativesDTOList = this.gson.fromJson(json, listOfObject);
        }
        return this.creativesDTOList;
    }

    public List<LineItemDTO> getLineItems(String token, String isAgAdv, Long id) throws Exception {
        this.responseDTO = this.apiCaller(bodyNull, HttpMethod.GET,String.format(this.apiURL.getLineItems(),isAgAdv,id),this.getHeaders(token)).getBody();
        if(this.responseDTO.getCode().equals(ApiCode.SUCCESS)) {
            Type listOfObject = new TypeToken<List<LineItemDTO>>(){}.getType();
            this.gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").setDateFormat("dd/MM/yyyy").create();
            String json = this.gson.toJson(this.responseDTO.getContent(), listOfObject);
            logger.info("Json Response :- " + json);
            this.lineItemDTOList = this.gson.fromJson(json, listOfObject);
        }
        return this.lineItemDTOList;
    }


    private ResponseEntity<ResponseDTO> apiCaller(Object body, HttpMethod httpMethod, String url, Map<String, String> headerMap) {
        logger.info("Url :- " + url + " and " + body);
        return this.restTemplate.exchange(url, httpMethod, new HttpEntity<>(!httpMethod.equals(HttpMethod.GET) ? body: null, this.fillHeader(headerMap)), ResponseDTO.class);
    }

    private HttpHeaders fillHeader(Map<String, String> headerMap) {
        HttpHeaders headers = new HttpHeaders();
        if (headerMap != null && headerMap.size() > 0) {
            Iterator<? extends Map.Entry<String, String>> iterator = headerMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, String> entry = iterator.next();
                headers.add(entry.getKey(), entry.getValue());
            }
        }
        return headers;
    }

    private Map<String, String> getHeaders(String token) {
        Map<String, String> params = new HashMap<>();
        params.put("Content-type", "application/json");
        params.put("Authorization", "Bearer "+token);
        return params;
    }

    public String uploadFileTos3bucket(File file, String fileName) throws Exception {
        logger.info("### uploading File ###" + fileName + "-" + this.awsProperties.getBucketName());
        String fileUrl = this.awsProperties.getEndPoint() + "/" + this.awsProperties.getBucketName() + "/" + fileName;
        this.amazonS3.putObject(new PutObjectRequest(this.awsProperties.getBucketName(), fileName, file).withCannedAcl(CannedAccessControlList.PublicRead));
        logger.info("### uploading  successfully ###");
        return fileUrl;
    }

    public String uploadFileTos3GeoJsonbucket(String geojson, String key) throws Exception {
        File temp = File.createTempFile(key, ".txt");
        BufferedWriter bw = new BufferedWriter(new FileWriter(temp));
        bw.write(geojson);
        bw.close();
        String fileUrl = "https://geojsonbukcet.s3.amazonaws.com" + "/" + key+".txt";
        this.amazonS3.putObject(new PutObjectRequest(this.awsProperties.getGeojsonbukcet(), key+".txt", temp).withCannedAcl(CannedAccessControlList.PublicRead));
        logger.info("### uploading  successfully ###");
        temp.deleteOnExit();
        return fileUrl;
    }

    public String getAtter(String jsonStr) {
        JsonParser parser = new JsonParser();
        JsonObject mainObject = parser.parse(jsonStr).getAsJsonObject();
        if(this.hasKeyValue(mainObject, FEATURES)) {
            JsonArray array = mainObject.getAsJsonArray(FEATURES);
            if(array != null && array.size() > 0) {
                JsonObject jsonObject = array.get(0).getAsJsonObject();
                if(this.hasKeyValue(jsonObject,GEOMETRY)) {
                    JsonObject geometeryObject = jsonObject.getAsJsonObject(GEOMETRY);
                    if(this.hasKeyValue(geometeryObject, COORDINATES)) {
                        JsonArray coordinatesObject = geometeryObject.getAsJsonArray(COORDINATES);
                        coordinatesObject = coordinatesObject.get(0).getAsJsonArray();
                        StringBuilder stringBuilder = new StringBuilder();
                        for(int i = 0; i <coordinatesObject.size(); i ++) {
                            JsonArray point = coordinatesObject.get(i).getAsJsonArray();
                            stringBuilder.append(point.get(1).getAsString());
                            stringBuilder.append(" ");
                            stringBuilder.append(point.get(0).getAsString());
                            stringBuilder.append(",");
                        }
                        return stringBuilder.toString();
                    }
                }
            }
        }
        return "";
    }

    private Boolean hasKeyValue(JsonObject jsonObj, String key) {
        return ((jsonObj.has(key) && jsonObj.get(key) != null && !jsonObj.get(key).isJsonNull()) &&
            ((jsonObj.get(key).isJsonObject() && !jsonObj.getAsJsonObject(key).entrySet().isEmpty()) ||
            (jsonObj.get(key).isJsonArray() && 0 < jsonObj.getAsJsonArray(key).size()) ||
            (jsonObj.get(key).isJsonPrimitive())));
    }


    public File convertMultiPartToFile(MultipartFile file) throws IOException {
        File convFile = new File(file.getOriginalFilename());
        FileOutputStream fos = new FileOutputStream(convFile);
        fos.write(file.getBytes());
        fos.close();
        return convFile;
    }

    public static void main(String args[]) {
        String json = "{\"features\":[{\"geometry\":{\"coordinates\":[[[-74.089522,40.757272],[-74.0919578,40.7579934],[-74.0921509,40.7567743],[-74.0893507,40.756815],[-74.087044,40.75714],[-74.0853918,40.7575626],[-74.0857887,40.7581884],[-74.0870225,40.7578146],[-74.0891361,40.7577333],[-74.0915394,40.7578471],[-74.0919578,40.7579934],[-74.089522,40.757272]]],\"type\":\"polygon\"},\"type\":\"Feature\",\"properties\":{}}],\"type\":\"FeatureCollection\"}";
        MicroServicesDetail microServicesDetail = new MicroServicesDetail();
        System.out.println(microServicesDetail.getAtter(json));
    }
}
