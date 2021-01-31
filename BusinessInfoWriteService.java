package org.quorum.service.imp;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.quorum.domain.dto.BulkRequest;
import org.quorum.domain.dto.BulkSegmentDetailDto;
import org.quorum.entity.dto.*;
import org.quorum.entity.dto.BulkDTO.BulkSegmentPoiListDTO;
import org.quorum.entity.dto.BulkDTO.BulkSegmentScheduleDTO;
import org.quorum.entity.dto.BulkDTO.SegmentListingDTO;
import org.quorum.entity.dto.BulkDTO.UserPermissionsDTO;
import org.quorum.entity.enums.SegmentType;
import org.quorum.entity.enums.UserType;
import org.quorum.entity.util.ApiConstants;
import org.quorum.service.IWriteDataService;
import org.quorum.util.BulkProcessingServiceUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;


@Service
@Scope("prototype")
public class BusinessInfoWriteService extends PoiWrokBookUtil implements IWriteDataService {

    public Logger logger = LogManager.getLogger(BusinessInfoWriteService.class);

    @Autowired
    private MicroServicesDetail microServicesDetail;
    @Autowired
    private BulkProcessingServiceUtil bulkProcessingServiceUtil;
    private SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");
    private String isAgAdv = "0";
    private XSSFWorkbook workbook;
    private BulkRequest bulkRequest;
    private String pois[];
    private BulkSegmentDetailDto bulkSegmentDetailDto;
    private List<AgencyAdvertiserDTO> agencyAdvertiserList;
    private List<SegmentListingDTO> segmentGeoPathListDownload;
    private List<SegmentListingDTO> segmentOtherListDownload;
    private List<PoiDTO> poiDTOListDownload;
    private List<CampaignDTO> campaignDTOListDownload;
    private List<LineItemDTO> lineItemDTOLdistDownload;
    private List<CreativesDTO> creativesDTOListDownload;

    @Override
    public ByteArrayInputStream write(BulkRequest bulkRequest) throws Exception {
        this.bulkRequest = bulkRequest;
        // work book create
        this.workbook = new XSSFWorkbook();
        // stream for write the detail into file
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if(this.bulkRequest.getEntity().containsKey(ID_KEY) && !(Long.valueOf(this.bulkRequest.getEntity().get(ID_KEY).toString()) == 0)) {
            this.isAgAdv = "1";
        }
        UserPermissionsDTO userPermissionsDTO = this.microServicesDetail.getCurrentUserPermissions(this.bulkRequest.getToken());
        if(userPermissionsDTO != null
                && userPermissionsDTO.getUserType() != null
                && userPermissionsDTO.getUserType().value == UserType.Agency.value){
            this.writeAgencyAdvertiserSheet();
        }
        // note if any of the service not work just commit the method will not download
        this.writeSegmentsGeoPathSheet();
        this.writeSegmentsOthersSheet();
        this.writePoiSheet();
        this.writeCampaignsSheet();
        this.writeLineItemSheet();
        this.writeManageCreativesSheet();
        this.workbook.write(out);        
        ByteArrayInputStream bais = new ByteArrayInputStream(out.toByteArray());
        out.close();
        this.workbook.close();
        return bais;
    }

    private void writeAgencyAdvertiserSheet() throws Exception {
        // create the sheet for work-book
        XSSFSheet agency_adv_sheet = this.workbook.createSheet(AGENCY_ADVERTISER);
        CellStyle cellStyle = this.cellHeadingBackgroundColorStyle(IndexedColors.BLACK.getIndex(), agency_adv_sheet);
        Row headerRow = agency_adv_sheet.createRow(0);
        this.fillHeading(agency_adv_sheet, headerRow, cellStyle, 0, 60*255, AGENCY_ADVERTISER, DOUBLE_A, true);
        // sub header
        cellStyle = this.cellHeadingBackgroundColorStyle(IndexedColors.BLUE_GREY.getIndex(), agency_adv_sheet);
        headerRow = agency_adv_sheet.createRow(1);
        this.fillHeading(agency_adv_sheet, headerRow, cellStyle, 0, 20*255, ADVERTISER_ID, null, false);
        this.fillHeading(agency_adv_sheet, headerRow, cellStyle, 1, 40*255, COMPANY_NAME, null, false);
        this.fillHeading(agency_adv_sheet, headerRow, cellStyle, 2, 30*255, EMAIL, null, false);
        this.fillHeading(agency_adv_sheet, headerRow, cellStyle, 3, 30*255, FIRST_NAME, null, false);
        this.fillHeading(agency_adv_sheet, headerRow, cellStyle, 4, 30*255, LAST_NAME, null, false);
        this.fillHeading(agency_adv_sheet, headerRow, cellStyle, 5, 50*255, LOGO_URL, null, false);
        this.fillHeading(agency_adv_sheet, headerRow, cellStyle, 6, 50*255, COMPANY_WEBSITE, null, false);
        // calling the api for get the data ==> if user is agency- then value of key is 1
        if(this.isAgAdv.equals("1")) {
            this.agencyAdvertiserList = this.microServicesDetail.getAgencyAdvertiser(this.bulkRequest.getToken());
            CellStyle simple_style = this.cellBodyColorStyle(agency_adv_sheet);
            Integer rows = 2; // start adding data into rows
            if(this.agencyAdvertiserList != null && this.agencyAdvertiserList.size() > 0) {
                for (AgencyAdvertiserDTO agencyAdvertiser: this.agencyAdvertiserList) {
                    Row row = agency_adv_sheet.createRow(rows);
                    this.fillCellValue(0, row, simple_style, agencyAdvertiser.getId());
                    this.fillCellValue(1, row, simple_style, agencyAdvertiser.getCompName());
                    this.fillCellValue(2, row, simple_style, agencyAdvertiser.getEmail());
                    this.fillCellValue(3, row, simple_style, agencyAdvertiser.getFirstName());
                    this.fillCellValue(4, row, simple_style, agencyAdvertiser.getLastName());
                    this.fillCellValue(5, row, simple_style, agencyAdvertiser.getLogoUrl());
                    this.fillCellValue(6, row, simple_style, agencyAdvertiser.getWebURL());
                    rows = rows+1;
                }
            }
        }
    }

    private void writeSegmentsGeoPathSheet() throws Exception {
        // create the sheet for work-book
        XSSFSheet segment_geo_path_sheet = this.workbook.createSheet(SEGMENTS_GEOPATH);
        CellStyle cellStyle = this.cellHeadingBackgroundColorStyle(IndexedColors.BLACK.getIndex(), segment_geo_path_sheet);
        Row headerRow = segment_geo_path_sheet.createRow(0);
        this.fillHeading(segment_geo_path_sheet, headerRow, cellStyle, 0, 80*255, SEGMENTS_GEOPATH, DOUBLE_A, true);
        this.fillHeading(segment_geo_path_sheet, headerRow, cellStyle, 2, 100*255, FLIGHT_DETAIL, DOUBLE_CD, true);
        this.fillDropDownValue(segment_geo_path_sheet, headerRow.getRowNum(), 7, PROCESS_TYPE_LIST);
        this.fillHeading(segment_geo_path_sheet, headerRow, cellStyle, 8, 20*255, TOTAL_PREVIOUS_DAYS_NOTE, null, false);
        this.fillDropDownValue(segment_geo_path_sheet, headerRow.getRowNum(), 9, EXPIRY_TYPE_LIST);
        this.fillHeading(segment_geo_path_sheet, headerRow, cellStyle, 10, 30*255, DEVICE_EXPIRE_DAYS_NOTE, null, false);
        this.fillDropDownValue(segment_geo_path_sheet, headerRow.getRowNum(), 11, TIME_ZONE_LIST);
        this.fillDropDownValue(segment_geo_path_sheet, headerRow.getRowNum(), 15, ALGO_LIST);
        // sub header
        cellStyle = this.cellHeadingBackgroundColorStyle(IndexedColors.BLUE_GREY.getIndex(), segment_geo_path_sheet);
        headerRow = segment_geo_path_sheet.createRow(1);
        int coumn = 0;
        this.fillHeading(segment_geo_path_sheet, headerRow, cellStyle, coumn++, 20*255, SEGMENT_ID, null, false);
        this.fillHeading(segment_geo_path_sheet, headerRow, cellStyle, coumn++, 60*255, NAME, null, false);
        this.fillHeading(segment_geo_path_sheet, headerRow, cellStyle, coumn++, 30*255, DESCRIPTION, null, false);
        this.fillHeading(segment_geo_path_sheet, headerRow, cellStyle, coumn++, 100*255, FLIGHT, null, false);
        this.fillHeading(segment_geo_path_sheet, headerRow, cellStyle, coumn++, 30*255, BILLBOARD_IMAGE_URL, null, false);
        this.fillHeading(segment_geo_path_sheet, headerRow, cellStyle, coumn++, 30*255, GEOPATH_ID, null, false);
        this.fillHeading(segment_geo_path_sheet, headerRow, cellStyle, coumn++, 50*255, POIS, null, false);
        this.fillHeading(segment_geo_path_sheet, headerRow, cellStyle, coumn++, 20*255, PROCESS_TYPE, null, false);
        this.fillHeading(segment_geo_path_sheet, headerRow, cellStyle, coumn++, 30*255, TOTAL_PREVIOUS_DAY, null, false);
        this.fillHeading(segment_geo_path_sheet, headerRow, cellStyle, coumn++, 30*255, EXPIRY_TYPE, null, false);
        this.fillHeading(segment_geo_path_sheet, headerRow, cellStyle, coumn++, 20*255, DEVICE_EXPIRE_DAYS, null, false);
        this.fillHeading(segment_geo_path_sheet, headerRow, cellStyle, coumn++, 20*255, TIME_ZONE, null, false);
        this.fillHeading(segment_geo_path_sheet, headerRow, cellStyle, coumn++, 20*255, GROUP, null, false);
        this.fillHeading(segment_geo_path_sheet, headerRow, cellStyle, coumn++, 20*255, CATEGORY, null, false);
        this.fillHeading(segment_geo_path_sheet, headerRow, cellStyle, coumn++, 20*255, BRAND, null, false);
        this.fillHeading(segment_geo_path_sheet, headerRow, cellStyle, coumn++, 20*255, ALGO, null, false);
        this.fillHeading(segment_geo_path_sheet, headerRow, cellStyle, coumn++, 20*255, SEGMENT_FLIGHT, null, false);
        this.fillHeading(segment_geo_path_sheet, headerRow, cellStyle, coumn, 20*255, REGION, null, false);

        // get the segment list
        this.bulkSegmentDetailDto = this.microServicesDetail.getAllSegment(this.bulkRequest.getToken(), this.isAgAdv, Long.valueOf(this.bulkRequest.getEntity().get(ID_KEY).toString()));
        if(this.bulkSegmentDetailDto.getPoiList() != null && this.bulkSegmentDetailDto.getPoiList().size() > 0) {
            int index = 0;
            pois = new String[this.bulkSegmentDetailDto.getPoiList().size()];
            for (BulkSegmentPoiListDTO bulkSegmentPoiListDTO: this.bulkSegmentDetailDto.getPoiList()) {
                if(bulkSegmentPoiListDTO.getId() != null && (bulkSegmentPoiListDTO.getName() != null && !bulkSegmentPoiListDTO.getName().equals(""))) {
                    String poiDetail = "("+bulkSegmentPoiListDTO.getId()+") " + bulkSegmentPoiListDTO.getName();
                    pois[index] = poiDetail;
                    index = index+1;
                }
            }
        }
        // separate the segment
        if(this.bulkSegmentDetailDto.getSegmentList() != null && this.bulkSegmentDetailDto.getSegmentList().size()>0) {
            this.segmentGeoPathListDownload = new ArrayList<>();
            this.segmentOtherListDownload = new ArrayList<>();
            for (SegmentListingDTO segmentListingDTO : this.bulkSegmentDetailDto.getSegmentList()) {
                if (segmentListingDTO.getSegmentType() == SegmentType.GeoPath.value) {
                    this.segmentGeoPathListDownload.add(segmentListingDTO);
                } else {
                    this.segmentOtherListDownload.add(segmentListingDTO);
                }
            }
        }
        // fill the detail of segment
        CellStyle simple_style = this.cellBodyColorStyle(segment_geo_path_sheet);
        Integer rows = 2; // start adding data into rows
        if(this.segmentGeoPathListDownload != null && this.segmentGeoPathListDownload.size() > 0) {
            for (SegmentListingDTO segmentListingDTO: this.segmentGeoPathListDownload) {
                Row row = segment_geo_path_sheet.createRow(rows);
                this.fillCellValue(0, row, simple_style, segmentListingDTO.getId());
                this.fillCellValue(1, row, simple_style, segmentListingDTO.getSegmentName());
                this.fillCellValue(2, row, simple_style, segmentListingDTO.getSegmentDescription());
                this.fillCellValue(3, row, simple_style, this.getFlights(segmentListingDTO.getSegmentSchedule()));
                this.fillCellValue(4, row, simple_style, segmentListingDTO.getImgURLBillboard());
                this.fillCellValue(5, row, simple_style, segmentListingDTO.getGeoPathId().toString());
                this.fillCellValue(6, row, simple_style, this.getAttachePois(segmentListingDTO.getAttachPois()));
                // process type define
                this.fillDropDownValue(segment_geo_path_sheet, row.getRowNum(), 7, PROCESS_TYPE_LIST);
                if(segmentListingDTO.getExactDate() != null) {
                    if(segmentListingDTO.getExactDate()) {
                        this.fillCellValue(7, row, simple_style, PROCESS_TYPE_LIST[1]);
                    } else {
                        this.fillCellValue(7, row, simple_style, PROCESS_TYPE_LIST[0]);
                    }
                }
                this.fillCellValue(8, row, simple_style, segmentListingDTO.getTotalDays());

                this.fillDropDownValue(segment_geo_path_sheet, row.getRowNum(), 9, EXPIRY_TYPE_LIST);
                if(segmentListingDTO.getExpiryDays() != null && segmentListingDTO.getExpiryDays() > 0) {
                    this.fillCellValue(9, row, simple_style, EXPIRY_TYPE_LIST[1]);
                } else {
                    this.fillCellValue(9, row, simple_style, EXPIRY_TYPE_LIST[0]);
                }
                if(segmentListingDTO.getExpiryDays() != null) {
                    this.fillCellValue(10, row, simple_style, segmentListingDTO.getExpiryDays());
                } else {
                    this.fillCellValue(10, row, simple_style, "");
                }
                //this.fillCellValue(9, row, simple_style, segmentListingDTO.getExpiryDays());
                this.fillDropDownValue(segment_geo_path_sheet, row.getRowNum(), 11, TIME_ZONE_LIST);
                this.fillCellValue(11, row, simple_style, segmentListingDTO.getTimeZone());
                this.fillCellValue(12, row, simple_style, segmentListingDTO.getGroupId());
                this.fillCellValue(13, row, simple_style, segmentListingDTO.getCategory());
                this.fillCellValue(14, row, simple_style, segmentListingDTO.getBrand());
                this.fillDropDownValue(segment_geo_path_sheet, row.getRowNum(), 15, ALGO_LIST);
                String algoType= "";
                if(segmentListingDTO.getAlgo().equalsIgnoreCase("clear channel")) {
                    algoType = "GeoPath";
                } else if(segmentListingDTO.getAlgo().equalsIgnoreCase("both")) {
                    algoType = "Both";
                } else {
                    algoType = "Default";
                }
                this.fillCellValue(15, row, simple_style, algoType);
                this.fillCellValue(16, row, simple_style, segmentListingDTO.getFlight());
                this.fillCellValue(17, row, simple_style, segmentListingDTO.getRegion());
                rows = rows+1;
            }
        }
    }

    private void writeSegmentsOthersSheet() throws Exception {
        // create the sheet for work-book
        XSSFSheet segment_other_sheet = this.workbook.createSheet(SEGMENTS_OTHERS);
        CellStyle cellStyle = this.cellHeadingBackgroundColorStyle(IndexedColors.BLACK.getIndex(), segment_other_sheet);
        Row headerRow = segment_other_sheet.createRow(0);
        this.fillHeading(segment_other_sheet, headerRow, cellStyle, 0, 80*255, SEGMENTS_OTHERS, DOUBLE_A, true);
        this.fillDropDownValue(segment_other_sheet, headerRow.getRowNum(), 2, TYPE_LIST); // TYPE OF SEGMENT
        this.fillHeading(segment_other_sheet, headerRow, cellStyle, 3, 130*255, FLIGHT_DETAIL, DOUBLE_DE, true);
        this.fillDropDownValue(segment_other_sheet, headerRow.getRowNum(), 12, PROCESS_TYPE_LIST);
        this.fillHeading(segment_other_sheet, headerRow, cellStyle, 13, 20*255, TOTAL_PREVIOUS_DAYS_NOTE, null, false);
        this.fillDropDownValue(segment_other_sheet, headerRow.getRowNum(), 14, EXPIRY_TYPE_LIST);
        this.fillHeading(segment_other_sheet, headerRow, cellStyle, 15, 30*255, DEVICE_EXPIRE_DAYS_NOTE, null, false);
        this.fillDropDownValue(segment_other_sheet, headerRow.getRowNum(), 16, TIME_ZONE_LIST);
        this.fillDropDownValue(segment_other_sheet, headerRow.getRowNum(), 21, ALGO_LIST);
        // sub header
        cellStyle = this.cellHeadingBackgroundColorStyle(IndexedColors.BLUE_GREY.getIndex(), segment_other_sheet);
        headerRow = segment_other_sheet.createRow(1);
        int i = 0;
        this.fillHeading(segment_other_sheet, headerRow, cellStyle, i++, 20*255, SEGMENT_ID, null, false);
        this.fillHeading(segment_other_sheet, headerRow, cellStyle, i++, 60*255, NAME, null, false);
        this.fillHeading(segment_other_sheet, headerRow, cellStyle, i++, 30*255, TYPE, null, false);
        this.fillHeading(segment_other_sheet, headerRow, cellStyle, i++, 30*255, DESCRIPTION, null, false);
        this.fillHeading(segment_other_sheet, headerRow, cellStyle, i++, 100*255, FLIGHT, null, false);
        this.fillHeading(segment_other_sheet, headerRow, cellStyle, i++, 30*255, FULL_ADDRESS, null, false);
        this.fillHeading(segment_other_sheet, headerRow, cellStyle, i++, 20*255, LATITUDE, null, false);
        this.fillHeading(segment_other_sheet, headerRow, cellStyle, i++, 20*255, LONGITUDE, null, false);
        this.fillHeading(segment_other_sheet, headerRow, cellStyle, i++, 30*255, RADIUS, null, false);
        this.fillHeading(segment_other_sheet, headerRow, cellStyle, i++, 20*255, RADIUS_UNIT, null, false);
        this.fillHeading(segment_other_sheet, headerRow, cellStyle, i++, 50*255, GEO_JSON, null, false);
        this.fillHeading(segment_other_sheet, headerRow, cellStyle, i++, 50*255, POIS, null, false);
        this.fillHeading(segment_other_sheet, headerRow, cellStyle, i++, 20*255, PROCESS_TYPE, null, false);
        this.fillHeading(segment_other_sheet, headerRow, cellStyle, i++, 30*255, TOTAL_PREVIOUS_DAY, null, false);
        this.fillHeading(segment_other_sheet, headerRow, cellStyle, i++, 30*255, EXPIRY_TYPE, null, false);
        this.fillHeading(segment_other_sheet, headerRow, cellStyle, i++, 20*255, DEVICE_EXPIRE_DAYS, null, false);
        this.fillHeading(segment_other_sheet, headerRow, cellStyle, i++, 20*255, TIME_ZONE, null, false);
        this.fillHeading(segment_other_sheet, headerRow, cellStyle, i++, 20*255, GROUP, null, false);
        this.fillHeading(segment_other_sheet, headerRow, cellStyle, i++, 20*255, CATEGORY, null, false);
        this.fillHeading(segment_other_sheet, headerRow, cellStyle, i++, 20*255, BRAND, null, false);
        this.fillHeading(segment_other_sheet, headerRow, cellStyle, i++, 20*255, GEO_PATH_ID, null, false);
        this.fillHeading(segment_other_sheet, headerRow, cellStyle, i++, 20*255, ALGO, null, false);
        this.fillHeading(segment_other_sheet, headerRow, cellStyle, i++, 20*255, SEGMENT_FLIGHT, null, false);
        this.fillHeading(segment_other_sheet, headerRow, cellStyle, i, 20*255, REGION, null, false);
        CellStyle simple_style = this.cellBodyColorStyle(segment_other_sheet);
        Integer rows = 2; // start adding data into rows
        if(this.segmentOtherListDownload != null && this.segmentOtherListDownload.size() > 0) {
            for (SegmentListingDTO segmentListingDTO: this.segmentOtherListDownload) {
                Row row = segment_other_sheet.createRow(rows);
                this.fillCellValue(0, row, simple_style, segmentListingDTO.getId());
                this.fillCellValue(1, row, simple_style, segmentListingDTO.getSegmentName());
                this.fillDropDownValue(segment_other_sheet, row.getRowNum(), 2, TYPE_LIST);
                if (segmentListingDTO.getSegmentType() == SegmentType.GeoFance.value) {
                    this.fillCellValue(2, row, simple_style, TYPE_LIST[1]);
                } else {
                    this.fillCellValue(2, row, simple_style, TYPE_LIST[0]);
                }
                this.fillCellValue(3, row, simple_style, segmentListingDTO.getSegmentDescription());
                this.fillCellValue(4, row, simple_style, this.getFlights(segmentListingDTO.getSegmentSchedule()));
                this.fillCellValue(5, row, simple_style, segmentListingDTO.getFullAddress());
                this.fillCellValue(6, row, simple_style, segmentListingDTO.getLatitude());
                this.fillCellValue(7, row, simple_style, segmentListingDTO.getLongitude());
                this.fillCellValue(8, row, simple_style, segmentListingDTO.getRadius());
                if (segmentListingDTO.getUnit()!= null && !segmentListingDTO.getUnit().isEmpty() && segmentListingDTO.getUnit().equalsIgnoreCase(M)) {
                    this.fillCellValue(9, row, simple_style, METER);
                }
                if(segmentListingDTO.getGeoJson() != null && !segmentListingDTO.getGeoJson().equals("")) {
                    this.fillCellValue(10, row, simple_style, this.microServicesDetail.uploadFileTos3GeoJsonbucket(segmentListingDTO.getGeoJson(), UUID.randomUUID().toString()));
                }
                this.fillCellValue(11, row, simple_style, this.getAttachePois(segmentListingDTO.getAttachPois()));
                // process type define
                this.fillDropDownValue(segment_other_sheet, row.getRowNum(), 12, PROCESS_TYPE_LIST);
                if(segmentListingDTO.getExactDate() != null) {
                    if(segmentListingDTO.getExactDate()) {
                        this.fillCellValue(12, row, simple_style, PROCESS_TYPE_LIST[1]);
                    } else {
                        this.fillCellValue(12, row, simple_style, PROCESS_TYPE_LIST[0]);
                    }
                }
                this.fillCellValue(13, row, simple_style, segmentListingDTO.getTotalDays());
                this.fillDropDownValue(segment_other_sheet, row.getRowNum(), 14, EXPIRY_TYPE_LIST);
                if(segmentListingDTO.getExpiryDays() != null && segmentListingDTO.getExpiryDays() > 0) {
                    this.fillCellValue(14, row, simple_style, EXPIRY_TYPE_LIST[1]);
                } else {
                    this.fillCellValue(14, row, simple_style, EXPIRY_TYPE_LIST[0]);
                }
                if(segmentListingDTO.getExpiryDays() != null) {
                    this.fillCellValue(15, row, simple_style, segmentListingDTO.getExpiryDays());
                } else {
                    this.fillCellValue(15, row, simple_style, "");
                }
                this.fillDropDownValue(segment_other_sheet, row.getRowNum(), 16, TIME_ZONE_LIST);
                this.fillCellValue(16, row, simple_style, segmentListingDTO.getTimeZone());
                this.fillCellValue(17, row, simple_style, segmentListingDTO.getGroupId());
                this.fillCellValue(18, row, simple_style, segmentListingDTO.getCategory());
                this.fillCellValue(19, row, simple_style, segmentListingDTO.getBrand());
                if(segmentListingDTO.getAttachGeoPathId() != null) {
                    this.fillCellValue(20, row, simple_style, segmentListingDTO.getAttachGeoPathId().toString());
                }
                this.fillDropDownValue(segment_other_sheet, row.getRowNum(), 21, ALGO_LIST);
                String algoType= "";
                if(segmentListingDTO.getAlgo().equalsIgnoreCase("clear channel")) {
                    algoType = "GeoPath";
                } else if(segmentListingDTO.getAlgo().equalsIgnoreCase("both")) {
                    algoType = "Both";
                } else {
                    algoType = "Default";
                }
                this.fillCellValue(21, row, simple_style, algoType);
                this.fillCellValue(22, row, simple_style, segmentListingDTO.getFlight());
                this.fillCellValue(23, row, simple_style, segmentListingDTO.getRegion());
                rows = rows+1;
            }
        }
    }

    private void writePoiSheet() throws Exception {
        // create the sheet for work-book
        XSSFSheet poi_sheet = this.workbook.createSheet(POI);
        CellStyle cellStyle = this.cellHeadingBackgroundColorStyle(IndexedColors.BLACK.getIndex(), poi_sheet);
        Row headerRow = poi_sheet.createRow(0);
        this.fillHeading(poi_sheet, headerRow, cellStyle, 0, 20*255, POI, DOUBLE_A, true);
        cellStyle = this.cellHeadingBackgroundColorStyle(IndexedColors.BLUE_GREY.getIndex(), poi_sheet);
        headerRow = poi_sheet.createRow(1);
        this.fillHeading(poi_sheet, headerRow, cellStyle, 0, 20*255, POI_ID, null, false);
        this.fillHeading(poi_sheet, headerRow, cellStyle, 1, 60*255, NAME, null, false);
        this.fillHeading(poi_sheet, headerRow, cellStyle, 2, 30*255, FULL_ADDRESS, null, false);
        this.fillHeading(poi_sheet, headerRow, cellStyle, 3, 30*255, CITY, null, false);
        this.fillHeading(poi_sheet, headerRow, cellStyle, 4, 20*255, ZIP_CODE, null, false);
        this.fillHeading(poi_sheet, headerRow, cellStyle, 5, 30*255, LATITUDE, null, false);
        this.fillHeading(poi_sheet, headerRow, cellStyle, 6, 30*255, LONGITUDE, null, false);
        this.fillHeading(poi_sheet, headerRow, cellStyle, 7, 30*255, RADIUS, null, false);
        this.fillHeading(poi_sheet, headerRow, cellStyle, 8, 20*255, RADIUS_UNIT, null, false);
        this.fillHeading(poi_sheet, headerRow, cellStyle, 9, 20*255, GEO_JSON, null, false);
        this.fillHeading(poi_sheet, headerRow, cellStyle, 10, 15*255, GROUP, null, false);
        // fill data
        this.poiDTOListDownload = this.microServicesDetail.getPois(this.bulkRequest.getToken(), this.isAgAdv, Long.valueOf(this.bulkRequest.getEntity().get(ID_KEY).toString()));
        if (this.poiDTOListDownload != null && this.poiDTOListDownload.size() > 0) {
            CellStyle simple_style = this.cellBodyColorStyle(poi_sheet);
            Integer rows = 2; // start adding data into rows
            for (PoiDTO poiDTO: this.poiDTOListDownload) {
                Row row = poi_sheet.createRow(rows);
                this.fillCellValue(0, row, simple_style, poiDTO.getId());
                this.fillCellValue(1, row, simple_style, poiDTO.getName());
                this.fillCellValue(2, row, simple_style, poiDTO.getAddress());
                this.fillCellValue(3, row, simple_style, poiDTO.getCity());
                this.fillCellValue(4, row, simple_style, poiDTO.getZip());
                this.fillCellValue(5, row, simple_style, poiDTO.getLatitude());
                this.fillCellValue(6, row, simple_style, poiDTO.getLongitude());
                this.fillCellValue(7, row, simple_style, poiDTO.getRadius());
                if (poiDTO.getUnit()!= null && !poiDTO.getUnit().isEmpty() && poiDTO.getUnit().equalsIgnoreCase(M)) {
                    this.fillCellValue(8, row, simple_style, METER);
                }
                if(poiDTO.getGeoJson() != null && !poiDTO.getGeoJson().equals("")) {
                    this.fillCellValue(9, row, simple_style, this.microServicesDetail.uploadFileTos3GeoJsonbucket(poiDTO.getGeoJson(), UUID.randomUUID().toString()));
                }
                this.fillCellValue(10, row, simple_style, poiDTO.getGroupId());
                rows = rows+1;
            }
        }
    }

    private void writeCampaignsSheet() throws Exception {
        // create the sheet for work-book
        XSSFSheet campaign_sheet = this.workbook.createSheet(CAMPAIGNS);
        CellStyle cellStyle = this.cellHeadingBackgroundColorStyle(IndexedColors.BLACK.getIndex(), campaign_sheet);
        Row headerRow = campaign_sheet.createRow(0);
        this.fillHeading(campaign_sheet, headerRow, cellStyle, 0, 30*255, CAMPAIGNS, DOUBLE_A, true);
        this.fillDropDownValue(campaign_sheet, headerRow.getRowNum(), 2, CAMPAIGNS_STATE);
        this.fillDropDownValue(campaign_sheet, headerRow.getRowNum(), 3, CAMPAIGNS_TYPE);
        this.fillDropDownValue(campaign_sheet, headerRow.getRowNum(), 4, FB_OBJECTIVE_TYPE);
        this.fillDropDownValue(campaign_sheet, headerRow.getRowNum(), 5, BUDGET_TYPE_LIST);
        this.fillDropDownValue(campaign_sheet, headerRow.getRowNum(), 6, BILLING_PERIOD_LIST);
        cellStyle = this.cellHeadingBackgroundColorStyle(IndexedColors.BLUE_GREY.getIndex(), campaign_sheet);
        headerRow = campaign_sheet.createRow(1);
        this.fillHeading(campaign_sheet, headerRow, cellStyle, 0, 30*255, CAMPAIGN_ID, null, false);
        this.fillHeading(campaign_sheet, headerRow, cellStyle, 1, 60*255, NAME, null, false);
        this.fillHeading(campaign_sheet, headerRow, cellStyle, 2, 20*255, STATE, null, false);
        this.fillHeading(campaign_sheet, headerRow, cellStyle, 3, 20*255, TYPE, null, false);
        this.fillHeading(campaign_sheet, headerRow, cellStyle, 4, 30*255, FB_OBJECTIVE, null, false);
        this.fillHeading(campaign_sheet, headerRow, cellStyle, 5, 30*255, BUDGET_TYPE, null, false);
        this.fillHeading(campaign_sheet, headerRow, cellStyle, 6, 30*255, BILLING_PERIOD, null, false);
        this.fillHeading(campaign_sheet, headerRow, cellStyle, 7, 30*255, BUDGET, null, false);
        this.fillHeading(campaign_sheet, headerRow, cellStyle, 8, 30*255, START_DATE, null, false);
        this.fillHeading(campaign_sheet, headerRow, cellStyle, 9, 30*255, END_DATE, null, false);
        this.fillHeading(campaign_sheet, headerRow, cellStyle, 10, 100*255, LINE_ITEM, null, false);
        // fill detail
        this.campaignDTOListDownload = this.microServicesDetail.getAllCampaign(this.bulkRequest.getToken(), this.isAgAdv, Long.valueOf(this.bulkRequest.getEntity().get(ID_KEY).toString()));
        if (this.campaignDTOListDownload != null && this.campaignDTOListDownload.size() > 0) {
            CellStyle simple_style = this.cellBodyColorStyle(campaign_sheet);
            Integer rows = 2; // start adding data into rows
            for (CampaignDTO campaignDTO: this.campaignDTOListDownload) {
                Row row = campaign_sheet.createRow(rows);
                this.fillCellValue(0, row, simple_style, campaignDTO.getId());
                this.fillCellValue(1, row, simple_style, campaignDTO.getName());
                this.fillDropDownValue(campaign_sheet, row.getRowNum(), 2, CAMPAIGNS_STATE);
                this.fillCellValue(2, row, simple_style, campaignDTO.getStatus());
                this.fillDropDownValue(campaign_sheet, row.getRowNum(), 3, CAMPAIGNS_TYPE);
                if (campaignDTO.getType() == ApiConstants.FACEBOOK) {
                    this.fillCellValue(3, row, simple_style, CAMPAIGNS_TYPE[1]);
                } else {
                    this.fillCellValue(3, row, simple_style, CAMPAIGNS_TYPE[0]);
                }
                this.fillDropDownValue(campaign_sheet, row.getRowNum(), 4, FB_OBJECTIVE_TYPE);
                if(campaignDTO.getObjective() != null && !campaignDTO.getObjective().equals("")) {
                    if(campaignDTO.getType() == ApiConstants.FACEBOOK) {
                        this.fillCellValue(4, row, simple_style, FB_OBJECTIVE_TYPE[0]);
                    } else {
                        this.fillCellValue(4, row, simple_style, campaignDTO.getObjective());
                    }
                } else {
                     if(campaignDTO.getType() != null && campaignDTO.getType() == ApiConstants.FACEBOOK)  {
                         this.fillCellValue(4, row, simple_style, FB_OBJECTIVE_TYPE[0]);
                    }
                }
                this.fillDropDownValue(campaign_sheet, row.getRowNum(), 5, BUDGET_TYPE_LIST);
                String bType = BUDGET_TYPE_LIST[1];
                if (campaignDTO.getBudget() != null && campaignDTO.getBudget() > 0) { bType = BUDGET_TYPE_LIST[0]; }
                if(campaignDTO.getType() != ApiConstants.FACEBOOK) { this.fillCellValue(5, row, simple_style, bType); }
                this.fillDropDownValue(campaign_sheet, row.getRowNum(), 6, BILLING_PERIOD_LIST);
                String bPeriod = BILLING_PERIOD_LIST[0];
                if (campaignDTO.getEndDate() != null) { bPeriod = BILLING_PERIOD_LIST[1]; }
                if(campaignDTO.getType() != ApiConstants.FACEBOOK){ this.fillCellValue(6, row, simple_style, bPeriod); }
                if (campaignDTO.getBudget() != null && campaignDTO.getBudget() > 0) { this.fillCellValue(7, row, simple_style, campaignDTO.getBudget()); }
                if(campaignDTO.getStartDate() != null) {
                    this.fillCellValue(8, row, simple_style, campaignDTO.getStartDate().toString().split(" ")[0].trim());
                }
                if(campaignDTO.getEndDate() != null) {
                    this.fillCellValue(9, row, simple_style, campaignDTO.getEndDate().toString().split(" ")[0].trim());
                }
                String lis = "";
                if (campaignDTO.getLineItem() != null && campaignDTO.getLineItem().size() > 0) {
                    List<ViewLineItemDTO> dtos = campaignDTO.getLineItem();
                    for (ViewLineItemDTO li : dtos) { lis += li.getId() + ","; }
                    if (lis.length() > 1) { lis = lis.substring(0, lis.length() - 1); }
                }
                this.fillCellValue(10, row, simple_style, lis);

                rows = rows+1;
            }
        }
    }

    private void writeLineItemSheet() throws Exception {
        // create the sheet for work-book
        XSSFSheet line_item_sheet = this.workbook.createSheet(LINE_ITEM);
        CellStyle cellStyle = this.cellHeadingBackgroundColorStyle(IndexedColors.BLACK.getIndex(), line_item_sheet);
        Row headerRow = line_item_sheet.createRow(0);
        this.fillHeading(line_item_sheet, headerRow, cellStyle, 0, 60*255, LINE_ITEM, DOUBLE_A, true);
        this.fillDropDownValue(line_item_sheet, headerRow.getRowNum(), 2, CAMPAIGNS_TYPE);
        this.fillDropDownValue(line_item_sheet, headerRow.getRowNum(), 6, REVENUE_TYPE_LIST);
        this.fillDropDownValue(line_item_sheet, headerRow.getRowNum(), 8, LINE_ITEM_BUDGETS_TYPE);
        this.fillDropDownValue(line_item_sheet, headerRow.getRowNum(), 16, OPTIMIZATION_METHOD_LIST);
        this.fillDropDownValue(line_item_sheet, headerRow.getRowNum(), 18, GOAL_PRIORITY_LIST);

        cellStyle = this.cellHeadingBackgroundColorStyle(IndexedColors.BLUE_GREY.getIndex(), line_item_sheet);
        headerRow = line_item_sheet.createRow(1);
        this.fillHeading(line_item_sheet, headerRow, cellStyle, 0, 30*255, LINE_ITEM_ID, null, false);
        this.fillHeading(line_item_sheet, headerRow, cellStyle, 1, 60*255, NAME, null, false);
        this.fillHeading(line_item_sheet, headerRow, cellStyle, 2, 20*255, TYPE, null, false);
        this.fillHeading(line_item_sheet, headerRow, cellStyle, 3, 30*255, GROUP, null, false);
        this.fillHeading(line_item_sheet, headerRow, cellStyle, 4, 30*255, CAMPAIGN, null, false);
        this.fillHeading(line_item_sheet, headerRow, cellStyle, 5, 30*255, SEGMENT, null, false);
        this.fillDropDownValue(line_item_sheet, headerRow.getRowNum(), 6, REVENUE_TYPE_LIST);
        this.fillHeading(line_item_sheet, headerRow, cellStyle, 6, 30*255, REVENUE_TYPE, null, false);
        this.fillHeading(line_item_sheet, headerRow, cellStyle, 7, 30*255, REVENUE_VALUE, null, false);
        this.fillHeading(line_item_sheet, headerRow, cellStyle, 8, 30*255, BUDGET_TYPE, null, false);
        this.fillHeading(line_item_sheet, headerRow, cellStyle, 9, 30*255, DAILY_BUDGET, null, false);
        this.fillHeading(line_item_sheet, headerRow, cellStyle, 10, 30*255, MIN_BUDGET, null, false);
        this.fillHeading(line_item_sheet, headerRow, cellStyle, 11, 30*255, MAX_BUDGET, null, false);
        this.fillHeading(line_item_sheet, headerRow, cellStyle, 12, 30*255, START_DATE, null, false);
        this.fillHeading(line_item_sheet, headerRow, cellStyle, 13, 30*255, END_DATE, null, false);
        this.fillHeading(line_item_sheet, headerRow, cellStyle, 14, 60*255, CREATIVES, null, false);
        this.fillHeading(line_item_sheet, headerRow, cellStyle, 15, 30*255, EXCLUDED_PUBLISHERS, null, false);
        this.fillHeading(line_item_sheet, headerRow, cellStyle, 16, 30*255, OPTIMIZATION_METHOD, null, false);
        this.fillHeading(line_item_sheet, headerRow, cellStyle, 17, 30*255, OPTIMIZATION_AMOUNT, null, false);
        this.fillHeading(line_item_sheet, headerRow, cellStyle, 18, 30*255, GOAL_PRIORITY, null, false);
        //this.fillHeading(line_item_sheet, headerRow, cellStyle, 19, 30*255, COUNTRY_TARGET, null, false);

        this.lineItemDTOLdistDownload = this.microServicesDetail.getLineItems(this.bulkRequest.getToken(), isAgAdv, Long.valueOf(this.bulkRequest.getEntity().get(ID_KEY).toString()));
        if (this.lineItemDTOLdistDownload != null && this.lineItemDTOLdistDownload.size() > 0) {
            CellStyle simple_style = this.cellBodyColorStyle(line_item_sheet);
            Integer rows = 2; // start adding data into rows
            for (LineItemDTO lineItemDTO: this.lineItemDTOLdistDownload) {
                Row row = line_item_sheet.createRow(rows);
                this.fillCellValue(0, row, simple_style, lineItemDTO.getId());
                this.fillCellValue(1, row, simple_style, lineItemDTO.getName());
                this.fillDropDownValue(line_item_sheet, row.getRowNum(), 2, CAMPAIGNS_TYPE);
                if(lineItemDTO.getType() == ApiConstants.WEB) { // for web and facebook
                    this.fillCellValue(2, row, simple_style, CAMPAIGNS_TYPE[0]);
                } else {
                    this.fillCellValue(2, row, simple_style, CAMPAIGNS_TYPE[1]);
                }
                this.fillCellValue(3, row, simple_style, lineItemDTO.getAdvertiserGroupId());
                this.fillCellValue(4, row, simple_style, lineItemDTO.getCampaignId());
                this.fillCellValue(5, row, simple_style, lineItemDTO.getSegmentId());
                this.fillDropDownValue(line_item_sheet, row.getRowNum(), 6, REVENUE_TYPE_LIST);
                if (lineItemDTO.getType() != ApiConstants.FACEBOOK && lineItemDTO.getRevenueType() != null) {
                    this.fillCellValue(6, row, simple_style, REVENUE_TYPE_LIST[0]);
                }
                if (lineItemDTO.getType() != ApiConstants.FACEBOOK && lineItemDTO.getRevenueValue() != null) {
                    this.fillCellValue(7, row, simple_style, lineItemDTO.getRevenueValue());
                }
                this.fillDropDownValue(line_item_sheet, row.getRowNum(), 8, LINE_ITEM_BUDGETS_TYPE);
                if(lineItemDTO.getType() != ApiConstants.FACEBOOK) {
                    if (lineItemDTO.getDailyBudget() != null || lineItemDTO.getDailyBudget() > 0) {
                        this.fillCellValue(8, row, simple_style, LINE_ITEM_BUDGETS_TYPE[0]);
                    } else {
                        this.fillCellValue(8, row, simple_style, LINE_ITEM_BUDGETS_TYPE[0]);
                    }
                }
                if (lineItemDTO.getType() != ApiConstants.FACEBOOK && lineItemDTO.getDailyBudget() != null) {
                    this.fillCellValue(9, row, simple_style, lineItemDTO.getDailyBudget());
                }
                if (lineItemDTO.getMinMargin() != null && lineItemDTO.getMinMargin() > 0) {
                    this.fillCellValue(10, row, simple_style, lineItemDTO.getMinMargin());
                }
                if (lineItemDTO.getMaxMargin() != null && lineItemDTO.getMaxMargin() > 0) {
                    this.fillCellValue(11, row, simple_style, lineItemDTO.getMaxMargin());
                }
                this.fillCellValue(12, row, simple_style, lineItemDTO.getStartDate().toString().split(" ")[0].trim().replace("/", "-"));
                if(lineItemDTO.getEndDate() != null && !lineItemDTO.getEndDate().equals("")) {
                    this.fillCellValue(13, row, simple_style, lineItemDTO.getEndDate().toString().split(" ")[0].trim().replace("/", "-"));
                }
                if (lineItemDTO.getCreatives() != null && lineItemDTO.getCreatives().size() > 0) {
                    String cres = "";
                    List<CreativesDTO> dtos = lineItemDTO.getCreatives();
                    for (CreativesDTO cre : dtos) { cres += cre.getId() + ","; }
                    if (cres.length() > 1) { cres = cres.substring(0, cres.length() - 1); }
                    this.fillCellValue(14, row, simple_style, cres);
                }
                if(lineItemDTO.getExcludedSellers() != null && lineItemDTO.getExcludedSellers().size() > 0) {
                    String listExcluded = lineItemDTO.getExcludedSellers().toString();
                    String value = listExcluded.substring(1,listExcluded.length()-1);
                    this.fillCellValue(15, row, simple_style, value.replace(" ", ""));
                }
                this.fillDropDownValue(line_item_sheet, row.getRowNum(), 16, OPTIMIZATION_METHOD_LIST);
                if(lineItemDTO.getOptimizationMethod() != null) {
                    if(lineItemDTO.getOptimizationMethod().equals("cpa")) {
                        this.fillCellValue(16, row, simple_style, OPTIMIZATION_METHOD_LIST[0]);
                    } else {
                        this.fillCellValue(16, row, simple_style, OPTIMIZATION_METHOD_LIST[1]);
                    }
                }
                this.fillCellValue(17, row, simple_style, lineItemDTO.getOptimizationAmount());

                this.fillDropDownValue(line_item_sheet, row.getRowNum(), 18, GOAL_PRIORITY_LIST);
//                if(lineItemDTO.getOptimizationAmount() != null) { commented by SA, optimizaion val has no link with priority
                    if(lineItemDTO.getGoalPiriority()) { 
                        this.fillCellValue(18, row, simple_style, GOAL_PRIORITY_LIST[1]);
                    } else {
                        this.fillCellValue(18, row, simple_style, GOAL_PRIORITY_LIST[0]);
                    }
//                }
                // COUNTRY-TARGET
                //this.fillCellValue(19, row, simple_style, COUNTRY_TARGET_VALUE);
                rows = rows+1;
            }
        }
    }

    private void writeManageCreativesSheet() throws Exception {
        // create the sheet for work-book
        XSSFSheet manage_creative_sheet = this.workbook.createSheet(MANAGE_CREATIVES);
        CellStyle cellStyle = this.cellHeadingBackgroundColorStyle(IndexedColors.BLACK.getIndex(), manage_creative_sheet);
        Row headerRow = manage_creative_sheet.createRow(0);
        this.fillHeading(manage_creative_sheet, headerRow, cellStyle, 0, 60*255, MANAGE_CREATIVES, DOUBLE_A, true);
        this.fillDropDownValue(manage_creative_sheet, headerRow.getRowNum(), 2, SIZE_CREATIVE);
        this.fillDropDownValue(manage_creative_sheet, headerRow.getRowNum(), 3, AUDIT_TYPE_LIST);
        // sub header
        cellStyle = this.cellHeadingBackgroundColorStyle(IndexedColors.BLUE_GREY.getIndex(), manage_creative_sheet);
        headerRow = manage_creative_sheet.createRow(1);
        this.fillHeading(manage_creative_sheet, headerRow, cellStyle, 0, 20*255, CREATIVE_ID, null, false);
        this.fillHeading(manage_creative_sheet, headerRow, cellStyle, 1, 40*255, NAME, null, false);
        this.fillHeading(manage_creative_sheet, headerRow, cellStyle, 2, 20*255, DIMENTIONS, null, false);
        this.fillHeading(manage_creative_sheet, headerRow, cellStyle, 3, 20*255, AUDIT_TYPE, null, false);
        this.fillHeading(manage_creative_sheet, headerRow, cellStyle, 4, 80*255, CLICK_URL, null, false);
        this.fillHeading(manage_creative_sheet, headerRow, cellStyle, 5, 80*255, IMG_URL, null, false);
        this.fillHeading(manage_creative_sheet, headerRow, cellStyle, 6, 100*255, LINE_ITEMS, null, false);
        // calling the api for get the data
        this.creativesDTOListDownload = this.microServicesDetail.getCreatives(this.bulkRequest.getToken(), Long.valueOf(this.bulkRequest.getEntity().get(ID_KEY).toString()));
        if (this.creativesDTOListDownload != null && this.creativesDTOListDownload.size() > 0) {
            CellStyle simple_style = this.cellBodyColorStyle(manage_creative_sheet);
            Integer rows = 2; // start adding data into rows
            for (CreativesDTO creativesDTO: this.creativesDTOListDownload) {
                Row row = manage_creative_sheet.createRow(rows);
                this.fillCellValue(0, row, simple_style, creativesDTO.getId());
                this.fillCellValue(1, row, simple_style, creativesDTO.getName());
                this.fillDropDownValue(manage_creative_sheet, row.getRowNum(), 2, SIZE_CREATIVE);
                this.fillCellValue(2, row, simple_style, creativesDTO.getWidth() + " x " + creativesDTO.getHeight());
                this.fillDropDownValue(manage_creative_sheet, row.getRowNum(), 3, AUDIT_TYPE_LIST);
                if(creativesDTO.getIsSelfAudited() != null) {
                    if(creativesDTO.getIsSelfAudited()) {
                        this.fillCellValue(3, row, simple_style, AUDIT_TYPE_LIST[1]);
                    } else {
                        this.fillCellValue(3, row, simple_style, AUDIT_TYPE_LIST[0]);
                    }
                }
                this.fillCellValue(4, row, simple_style, creativesDTO.getClickURL());
                this.fillCellValue(5, row, simple_style, creativesDTO.getImgURL());
                if (creativesDTO.getLineItemIds() != null && creativesDTO.getLineItemIds().size() > 0) {
                    String lineItemIds = creativesDTO.getLineItemIds().toString();
                    lineItemIds = lineItemIds.substring(1, lineItemIds.length()-1);
                    this.fillCellValue(6, row, simple_style, lineItemIds);
                }
                rows = rows+1;
            }
        }
    }

    private String getFlights(List<BulkSegmentScheduleDTO> flights) {
        StringBuilder stringBuilder = new StringBuilder();
        if(flights != null && flights.size()>0) {
            int comma = flights.size()-1;
            for(int i=0; i<flights.size(); i++) {
                BulkSegmentScheduleDTO flight = flights.get(i);
                String flightStr = this.bulkProcessingServiceUtil.segmentFlightFormat(flight.getStartDate(),
                    flight.getStartTime(), flight.getEndDate(), flight.getEndTime());
                stringBuilder.append(flightStr);
                // this is bz we not need the comma at the last
                if(i != comma) { stringBuilder.append(","); }
            }
        }
        return stringBuilder.toString();
    }

    private String getAttachePois(List<BulkSegmentPoiListDTO> pois) {
        StringBuilder stringBuilder = new StringBuilder();
        if(pois != null && pois.size()>0) {
            int comma = pois.size()-1;
            for(int i=0; i<pois.size(); i++) {
                BulkSegmentPoiListDTO poi = pois.get(i);
                if(poi.getId() != null) {
                    stringBuilder.append(poi.getId().toString());
                    // this is bz we not need the comma at the last
                    if(i != comma) { stringBuilder.append(","); }
                }
            }
        }
        return stringBuilder.toString();
    }

    public static void main(String args[]) {
        Date date = new Date();
        System.out.println(date);
        SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");
        System.out.println(formatter.format(date));
    }

}
