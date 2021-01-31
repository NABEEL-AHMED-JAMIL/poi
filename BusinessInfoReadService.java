package org.quorum.service.imp;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.org.apache.regexp.internal.RE;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.quorum.domain.dto.*;
import org.quorum.entity.base.ApiCode;
import org.quorum.entity.base.ResponseDTO;
import org.quorum.entity.domain.AdvertiserGroup;
import org.quorum.entity.domain.Campaign;
import org.quorum.entity.dto.*;
import org.quorum.entity.enums.SegmentType;
import org.quorum.entity.enums.Status;
import org.quorum.entity.enums.UserType;
import org.quorum.entity.util.ApiConstants;
import org.quorum.service.IReadDataService;
import org.quorum.socket.SocketServerComponent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static org.quorum.util.BulkProcessingServiceUtil.*;


@Service
@Scope("prototype")
public class BusinessInfoReadService extends PoiWrokBookUtil implements IReadDataService {

    public Logger logger = LogManager.getLogger(BusinessInfoReadService.class);

    @Autowired
    private Gson gson;
    @Autowired
    private MicroServicesDetail microServicesDetail;
    @Autowired
    private SocketServerComponent socketServerComponent;
    private CustomUserDetailsDTO authUser;
    private PoiSegmentDetailDto poiSegmentDetailDto;
    private SellerMemberIdDetailDto sellerMemberIdDetailDto;
    private CreativeDetailDto creativeDetailDto;
    private LineItemsDetailDto lineItemsDetailDto;
    private List<AgencyAdvertiserDTO> agencyAdvertiserList;
    private List<BillboardDetailsDTO> segmentGeoPathDTOList;
    private List<BillboardDetailsDTO> segmentOtherDTORList;
    private List<PoiDTO> poiDTOS;
    private List<CampaignDTO> campaignDTOList;
    private List<LineItemDTO> lineItemDTOLdist;
    private List<CreativesDTO> creativesDTOList;
    private BulkRequest bulkRequest;
    private String isAgAdv = "0";
    private Boolean isValidationFailed = false;
    private ResponseDTO responseDTO;
    private Workbook workbook;
    private Sheet sheet;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private SimpleDateFormat lineItemDateFormat = new SimpleDateFormat("dd/MM/yyyy");

    @Override
    public ResponseDTO read(BulkRequest bulkRequest) throws Exception {
        this.bulkRequest = bulkRequest;
        if(this.bulkRequest.getEntity().containsKey(ID_KEY) && !(Long.valueOf(this.bulkRequest.getEntity().get(ID_KEY).toString()) == 0)) {
            logger.info("User is Agency Advertiser");
            this.isAgAdv = "1";
        }
        // verify the user detail this is bz what if our user detail so save the rest of process time
        this.authUser = this.microServicesDetail.getCurrentLoginUser(bulkRequest.getToken());
        if(this.authUser != null) {
            this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage("Start Process For Bulk", ""));
            File file = this.microServicesDetail.convertMultiPartToFile((MultipartFile) this.bulkRequest.getEntity().get("file")); // convert multipart file to file
            this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage("Verifying Your Detail", ""));
            FileInputStream excelFile = new FileInputStream(file);
            this.workbook = new XSSFWorkbook(excelFile); // fill the stream of file into work-book
            if (this.workbook == null || this.workbook.getNumberOfSheets() == 0) {
                this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage("You Uploaded Empty File", ""));
                this.responseDTO = new ResponseDTO(false, ApiConstants.ERROR_MSG + ": You uploaded empty file", ApiCode.HTTP_400);
            } else {
                String fileUrl = this.microServicesDetail.uploadFileTos3bucket(file, UUID.randomUUID().toString()); // file send to the s3
                this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage("Your File Url :- " + fileUrl, ""));
                this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage("Verifying Sheet Base On Sheet Name Any Sheet Name Wrong Will Stop All Process", ""));
                for (int i = 0; i < this.workbook.getNumberOfSheets(); i++) {
                    String sheetName = this.workbook.getSheetAt(i).getSheetName();
                    if(sheetName.equals(AGENCY_ADVERTISER)) {
                        if((authUser.getUserType() != null && authUser.getUserType().value == UserType.Agency.value)) {
                            if (this.isAgAdv.equals("1")) {
                                logger.info("Verifying Sheet :- " + AGENCY_ADVERTISER);
                                this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage("Validating sheet: Agency Advertiser", ""));
                                this.sheet = this.workbook.getSheetAt(i);
                                this.validateAgencyAdvertiserSheet(); // verify the sheet header and then verify the data
                            }
                        } else {
                            this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage("Validating Sheet : Skip You Sheet Because You don't have permission to Add Agency Advertisers.", ""));
                        }
                    } else if(sheetName.equals(SEGMENTS_GEOPATH)) {
                        logger.info("Verifying Sheet :- " + SEGMENTS_GEOPATH);
                        this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage("Validating sheet: Segments-GeoPath", ""));
                        this.sheet = this.workbook.getSheetAt(i);
                        this.validateSegmentsGeoPathSheet(); // verify the sheet header and then verify the data
                    } else if(sheetName.equals(SEGMENTS_OTHERS)) {
                        logger.info("Verifying Sheet :- " + SEGMENTS_OTHERS);
                        this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage("Validating sheet: Segments-Others", ""));
                        this.sheet = this.workbook.getSheetAt(i);
                        this.validateSegmentsOthersSheet(); // verify the sheet header and then verify the data
                    } else if(sheetName.equals(POI)) {
                        logger.info("Verifying Sheet :- " + POI);
                        this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage("Validating sheet: POI", ""));
                        this.sheet = this.workbook.getSheetAt(i);
                        this.validatePoiSheet();  // verify the sheet header and then verify the data
                    } else if(sheetName.equals(CAMPAIGNS)) {
                        logger.info("Verifying Sheet :- " + CAMPAIGNS);
                        this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage("Validating sheet: Campaigns", ""));
                        this.sheet = this.workbook.getSheetAt(i);
                        this.validateCampaignsSheet();  // verify the sheet header and then verify the data
                    } else if (sheetName.equals(LINE_ITEM)) {
                        logger.info("Verifying Sheet :- " + LINE_ITEM);
                        this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage("Validating sheet: Line Items", ""));
                        this.sheet = this.workbook.getSheetAt(i);
                        this.validateLineItemSheet();  // verify the sheet header and then verify the data
                    } else if(sheetName.equals(MANAGE_CREATIVES)) {
                        logger.info("Verifying Sheet :- " + MANAGE_CREATIVES);
                        this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage("Validating sheet: Creatives", ""));
                        this.sheet = this.workbook.getSheetAt(i);
                        this.validateManageCreativesSheet();  // verify the sheet header and then verify the data
                    } else {
                        logger.info("File Have Some Issue");
                        this.isValidationFailed = true;
                        this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage("Process Stop Your File Have Wrong Sheet With Name", "Wrong Sheet Name "+ sheetName + "__"));
                        break;
                    }
                    // this stop the process
                    if(this.isValidationFailed) {
                        this.responseDTO = new ResponseDTO(false, ApiConstants.ERROR_MSG + ": Some Sheet Not Valid", ApiCode.HTTP_400);
                        break;
                    }
                }

                // false then allow the next process other wise not allow for transaction
                if(!this.isValidationFailed) {
                    logger.info("All Sheet Valid");
                    // start process
                    for (int i = 0; i < this.workbook.getNumberOfSheets(); i++) {
                        String sheetName = this.workbook.getSheetAt(i).getSheetName();
                        if(sheetName.equals(AGENCY_ADVERTISER) &&
                                (authUser.getUserType() != null &&
                                 authUser.getUserType().value == UserType.Agency.value)){
                            if(this.isAgAdv.equals("1")) {
                                this.processAgencyAdvertiserSheetData();
                            }
                        } else if(sheetName.equals(SEGMENTS_GEOPATH)) {
                            this.readSegmentsGeoPathSheet();
                        } else if(sheetName.equals(SEGMENTS_OTHERS)) {
                            this.readSegmentsOthersSheet();
                        } else if(sheetName.equals(POI)) {
                            this.readPoiSheet();
                        } else if(sheetName.equals(CAMPAIGNS)) {
                            this.readCampaignsSheet();
                        } else if (sheetName.equals(LINE_ITEM)) {
                            this.readLineItemSheet();
                        } else if(sheetName.equals(MANAGE_CREATIVES)) {
                            this.readManageCreativesSheet();
                        }
                    }
                    this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage("Process Done", ""));
                }
            }
            file.delete();
        } else {
            logger.info("User Not Found");
            this.responseDTO = new ResponseDTO(false, ApiConstants.ERROR_MSG + ": User Not Found", ApiCode.HTTP_400);
        }
        return this.responseDTO;
    }

    private void validateAgencyAdvertiserSheet() throws Exception {
        String detail = "";
        String message = "";
        this.agencyAdvertiserList = new ArrayList<>();
        if(this.sheet != null) {
            if (this.sheet.getLastRowNum() < 1) { // check the total row in the sheet if result zero it's mean sheet empty
                this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(),this.socketServerComponent.generateMessage("Agency Advertiser sheet is empty", ""));
                return;
            } else { // sheet have data so validation process start
                Integer row = -1;
                Iterator<Row> iterator = this.sheet.iterator();
                Row headingRow = null;
                while (iterator.hasNext()) {
                    row = row + 1; // row start from zero
                    Row currentRow = iterator.next();
                    if (row == 1) {
                        headingRow = currentRow;
                        if (currentRow.getPhysicalNumberOfCells() != 7) {
                            this.isValidationFailed = true;
                            message = "Error in Sheet: Agency Advertiser on row: " + (row+1) + ", Some headings missing";
                        } else {
                            if(!currentRow.getCell(0).getStringCellValue().equals(ADVERTISER_ID)) {
                                this.isValidationFailed = true;
                                message = "Agency Advertiser :- Heading are not present on proper place";
                                detail += "Error in Sheet: Agency Advertiser on row: " + (row+1) + ", and cell should be " + ADVERTISER_ID+"__";
                            }
                            if(!currentRow.getCell(1).getStringCellValue().equals(COMPANY_NAME)) {
                                this.isValidationFailed = true;
                                message = "Agency Advertiser :- Heading are not present on proper place";
                                detail += "Error in Sheet: Agency Advertiser on row: " + (row+1) + ", and cell should be " + COMPANY_NAME+"__";
                            }
                            if(!currentRow.getCell(2).getStringCellValue().equals(EMAIL)) {
                                this.isValidationFailed = true;
                                message = "Agency Advertiser :- Heading are not present on proper place";
                                detail += "Error in Sheet: Agency Advertiser on row: " + (row+1) + ", and cell should be " + EMAIL+"__";
                            }
                            if(!currentRow.getCell(3).getStringCellValue().equals(FIRST_NAME)) {
                                this.isValidationFailed = true;
                                message = "Agency Advertiser :- Heading are not present on proper place";
                                detail += "Error in Sheet: Agency Advertiser on row: " + (row+1) + ", and cell should be " + FIRST_NAME+"__";
                            }
                            if(!currentRow.getCell(4).getStringCellValue().equals(LAST_NAME)) {
                                this.isValidationFailed = true;
                                message = "Agency Advertiser :- Heading are not present on proper place";
                                detail += "Error in Sheet: Agency Advertiser on row: " + (row+1) + ", and cell should be " + LAST_NAME+"__";
                            }
                            if(!currentRow.getCell(5).getStringCellValue().equals(LOGO_URL)) {
                                this.isValidationFailed = true;
                                message = "Agency Advertiser :- Heading are not present on proper place";
                                detail += "Error in Sheet: Agency Advertiser on row: " + (row+1) + ", and cell should be " + LOGO_URL+"__";
                            }
                            if(!currentRow.getCell(6).getStringCellValue().equals(COMPANY_WEBSITE)) {
                                this.isValidationFailed = true;
                                message = "Agency Advertiser :- Heading are not present on proper place";
                                detail += "Error in Sheet: Agency Advertiser on row: " + (row+1) + ", and cell should be " + COMPANY_WEBSITE+"__";
                            }
                        }
                        if(this.isValidationFailed) {
                            this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, detail));
                            return;
                        }
                    } else if (row > 1) {
                        // now start validating the data
                        //====================================Row-Data-Collect=======================================
                        AgencyAdvertiserSheetData agencyAdvertiserSheetData = new AgencyAdvertiserSheetData();
                        Cell currentCell = currentRow.getCell(0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(0).getStringCellValue().equals(ADVERTISER_ID) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            agencyAdvertiserSheetData.setAdvertiserId(currentCell.getStringCellValue().replace(".0",""));
                        }
                        currentCell = currentRow.getCell(1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(1).getStringCellValue().equals(COMPANY_NAME) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            agencyAdvertiserSheetData.setCompanyName(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(2, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(2).getStringCellValue().equals(EMAIL) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            agencyAdvertiserSheetData.setEmail(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(3, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(3).getStringCellValue().equals(FIRST_NAME) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            agencyAdvertiserSheetData.setFirstName(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(4, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(4).getStringCellValue().equals(LAST_NAME) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            agencyAdvertiserSheetData.setLastName(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(5, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(5).getStringCellValue().equals(LOGO_URL) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            agencyAdvertiserSheetData.setLogoURL(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(6, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(6).getStringCellValue().equals(COMPANY_WEBSITE) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            agencyAdvertiserSheetData.setCompanyWebsite(currentCell.getStringCellValue());
                        }
                        //====================================Row-Data-Validate=======================================
                        AgencyAdvertiserDTO agencyAdvertiserDTO = new AgencyAdvertiserDTO();
                        // company id checking
                        if(agencyAdvertiserSheetData.getAdvertiserId() != null && !agencyAdvertiserSheetData.getAdvertiserId().equals("")) {
                            if(this.microServicesDetail.findByIdAndAgencyId(this.bulkRequest.getToken(), agencyAdvertiserSheetData.getAdvertiserId()) != null) {
                                agencyAdvertiserDTO.setId(Long.valueOf(agencyAdvertiserSheetData.getAdvertiserId()));
                            } else {
                                detail += "Error in Sheet: Agency Advertiser on row: " + (row + 1) + ", Advertiser with id: " + agencyAdvertiserSheetData.getAdvertiserId() + " don't exists!__";
                            }
                        }
                        // company name checking
                        if(agencyAdvertiserSheetData.getCompanyName() != null && !agencyAdvertiserSheetData.getCompanyName().equals("")) {
                            agencyAdvertiserDTO.setCompName(agencyAdvertiserSheetData.getCompanyName());
                        } else {
                            detail += "Error in Sheet: Agency Advertiser on row: " + (row + 1) + ", Company Name should not be empty__";
                        }
                        // email checking
                        if(agencyAdvertiserSheetData.getEmail() != null && !agencyAdvertiserSheetData.getEmail().equals("")) {
                            if(isValidEmail(agencyAdvertiserSheetData.getEmail())) {
                                agencyAdvertiserDTO.setEmail(agencyAdvertiserSheetData.getEmail());
                            } else {
                                detail += "Error in Sheet: Agency Advertiser on row: " + (row + 1) + ", Company Email not valid __";
                            }
                        } else {
                            detail += "Error in Sheet: Agency Advertiser on row: " + (row + 1) + ", Company Email should not be empty__";
                        }
                        // first name
                        if(agencyAdvertiserSheetData.getFirstName() != null && !agencyAdvertiserSheetData.getFirstName().equals("")) {
                            agencyAdvertiserDTO.setFirstName(agencyAdvertiserSheetData.getFirstName());
                        } else {
                            detail += "Error in Sheet: Agency Advertiser on row: " + (row + 1) + ", First Name should not be empty__";
                        }
                        // last name
                        if(agencyAdvertiserSheetData.getLastName() != null && !agencyAdvertiserSheetData.getLastName().equals("")) {
                            agencyAdvertiserDTO.setLastName(agencyAdvertiserSheetData.getLastName());
                        } else {
                            detail += "Error in Sheet: Agency Advertiser on row: " + (row + 1) + ", Last Name should not be empty__";
                        }
                        // logo url
                        if(agencyAdvertiserSheetData.getLogoURL() != null && !agencyAdvertiserSheetData.getLogoURL().equals("")) {
                            agencyAdvertiserDTO.setLogoUrl(agencyAdvertiserSheetData.getLogoURL());
                        }
                        // web-site
                        if((agencyAdvertiserSheetData.getCompanyWebsite() != null && !agencyAdvertiserSheetData.getCompanyWebsite().equals("")) && isValidUrl(agencyAdvertiserSheetData.getCompanyWebsite())) {
                            agencyAdvertiserDTO.setWebURL(agencyAdvertiserSheetData.getCompanyWebsite());
                        } else {
                            detail += "Error in Sheet: Agency Advertiser on row: " + (row + 1) + ", Web-Site Url should not be empty or invalid__";
                        }
                        // it's mean this row oky
                        if(detail.equals("")) { this.agencyAdvertiserList.add(agencyAdvertiserDTO); }
                    }
                }
            }
        } else {
            this.isValidationFailed = true;
            this.responseDTO = new ResponseDTO(false, ApiConstants.ERROR_MSG + ": Sheet Object Null", ApiCode.HTTP_400);
        }
        // final check if some
        if(!detail.equals("")) {
            this.isValidationFailed = true;
            message = "Plz Verify Your Agency Advertiser Some Data Wrong";
            this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, detail));
            return;
        }
    }
    // process the file user
    private void processAgencyAdvertiserSheetData() throws Exception {
        Integer row = 3;
        String message = "Saving Process Start For Agency-Advertiser";
        this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, ""));
        for(AgencyAdvertiserDTO agencyAdvertiserDTO: this.agencyAdvertiserList) {
            ResponseDTO responseDTO = this.microServicesDetail.saveAdvertisersDetail(this.bulkRequest.getToken(), agencyAdvertiserDTO, agencyAdvertiserDTO.getId() != null ? true : false);
            message = "In Agency Advertiser sheet result for row: " + (row) + " is: " + responseDTO.getMessage();
            this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, ""));
            row = row+1;
        }
    }

    private void validateSegmentsGeoPathSheet() throws Exception {
        String detail = "";
        String message = "";
        this.segmentGeoPathDTOList = new ArrayList<>();
        if(this.sheet != null) {
            if (this.sheet.getLastRowNum() < 1) { // check the total row in the sheet if result zero it's mean sheet empty
                this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage("Segments GeoPath sheet is empty", ""));
                return;
            } else { // sheet have data so validation process start
                Integer row = -1;
                Iterator<Row> iterator = this.sheet.iterator();
                Row headingRow = null;
                while (iterator.hasNext()) {
                    row = row + 1; // row start from zero
                    Row currentRow = iterator.next();
                    if (row == 1) {
                        headingRow = currentRow;
                        if (currentRow.getPhysicalNumberOfCells() != 18) {
                            this.isValidationFailed = true;
                            message = "Error in Sheet: Segments GeoPath on row: " + (row+1) + ", Some headings missing";
                        } else {
                            if(!currentRow.getCell(0).getStringCellValue().equals(SEGMENT_ID)) {
                                this.isValidationFailed = true;
                                message = "Segments GeoPath :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row+1) + ", and cell should be " + SEGMENT_ID+"__";
                            }
                            if(!currentRow.getCell(1).getStringCellValue().equals(NAME)) {
                                this.isValidationFailed = true;
                                message = "Segments GeoPath :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row+1) + ", and cell should be " + NAME+"__";
                            }
                            if(!currentRow.getCell(2).getStringCellValue().equals(DESCRIPTION)) {
                                this.isValidationFailed = true;
                                message = "Segments GeoPath :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row+1) + ", and cell should be " + DESCRIPTION+"__";
                            }
                            if(!currentRow.getCell(3).getStringCellValue().equals(FLIGHT)) {
                                this.isValidationFailed = true;
                                message = "Segments GeoPath :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row+1) + ", and cell should be " + FLIGHT+"__";
                            }
                            if(!currentRow.getCell(4).getStringCellValue().equals(BILLBOARD_IMAGE_URL)) {
                                this.isValidationFailed = true;
                                message = "Segments GeoPath :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row+1) + ", and cell should be " + BILLBOARD_IMAGE_URL+"__";
                            }
                            if(!currentRow.getCell(5).getStringCellValue().equals(GEOPATH_ID)) {
                                this.isValidationFailed = true;
                                message = "Segments GeoPath :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row+1) + ", and cell should be " + GEOPATH_ID+"__";
                            }
                            if(!currentRow.getCell(6).getStringCellValue().equals(POIS)) {
                                this.isValidationFailed = true;
                                message = "Segments GeoPath :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row+1) + ", and cell should be " + POIS+"__";
                            }
                            if(!currentRow.getCell(7).getStringCellValue().equals(PROCESS_TYPE)) {
                                this.isValidationFailed = true;
                                message = "Segments GeoPath :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row+1) + ", and cell should be " + PROCESS_TYPE+"__";
                            }
                            if(!currentRow.getCell(8).getStringCellValue().equals(TOTAL_PREVIOUS_DAY)) {
                                this.isValidationFailed = true;
                                message = "Segments GeoPath :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row+1) + ", and cell should be " + TOTAL_PREVIOUS_DAY+"__";
                            }
                            if(!currentRow.getCell(9).getStringCellValue().equals(EXPIRY_TYPE)) {
                                this.isValidationFailed = true;
                                message = "Segments GeoPath :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row+1) + ", and cell should be " + EXPIRY_TYPE+"__";
                            }
                            if(!currentRow.getCell(10).getStringCellValue().equals(DEVICE_EXPIRE_DAYS)) {
                                this.isValidationFailed = true;
                                message = "Segments GeoPath :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row+1) + ", and cell should be " + DEVICE_EXPIRE_DAYS+"__";
                            }
                            if(!currentRow.getCell(11).getStringCellValue().equals(TIME_ZONE)) {
                                this.isValidationFailed = true;
                                message = "Segments GeoPath :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row+1) + ", and cell should be " + TIME_ZONE+"__";
                            }
                            if(!currentRow.getCell(12).getStringCellValue().equals(GROUP)) {
                                this.isValidationFailed = true;
                                message = "Segments GeoPath :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row+1) + ", and cell should be " + GROUP+"__";
                            }
                            if(!currentRow.getCell(13).getStringCellValue().equals(CATEGORY)) {
                                this.isValidationFailed = true;
                                message = "Segments GeoPath :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row+1) + ", and cell should be " + CATEGORY+"__";
                            }
                            if(!currentRow.getCell(14).getStringCellValue().equals(BRAND)) {
                                this.isValidationFailed = true;
                                message = "Segments GeoPath :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row+1) + ", and cell should be " + BRAND+"__";
                            }
                            if(!currentRow.getCell(15).getStringCellValue().equals(ALGO)) {
                                this.isValidationFailed = true;
                                message = "Segments GeoPath :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row+1) + ", and cell should be " + ALGO+"__";
                            }
                            if(!currentRow.getCell(16).getStringCellValue().equals(SEGMENT_FLIGHT)) {
                                this.isValidationFailed = true;
                                message = "Segments GeoPath :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row+1) + ", and cell should be " + SEGMENT_FLIGHT+"__";
                            }
                            if(!currentRow.getCell(17).getStringCellValue().equals(REGION)) {
                                this.isValidationFailed = true;
                                message = "Segments GeoPath :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row+1) + ", and cell should be " + REGION+"__";
                            }
                        }
                        if(this.isValidationFailed) {
                            this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, detail));
                            return;
                        }
                    } else if (row > 1) {
                        // now start validating the data
                        //====================================Row-Data-Collect=======================================
                        SegmentSheetDetailDto segmentSheetDetailDto = new SegmentSheetDetailDto();
                        Cell currentCell = currentRow.getCell(0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(0).getStringCellValue().equals(SEGMENT_ID) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setSegmentId(currentCell.getStringCellValue().replace(".0",""));
                        }
                        currentCell = currentRow.getCell(1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(1).getStringCellValue().equals(NAME) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setSegmentName(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(2, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(2).getStringCellValue().equals(DESCRIPTION) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setSegmentDescription(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(3, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(3).getStringCellValue().equals(FLIGHT) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setSegmentFlight(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(4, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(4).getStringCellValue().equals(BILLBOARD_IMAGE_URL) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setSegmentBillboardImageURL(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(5, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(5).getStringCellValue().equals(GEOPATH_ID) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setSegmentGeopathId(currentCell.getStringCellValue().replace(".0",""));
                        }
                        currentCell = currentRow.getCell(6, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(6).getStringCellValue().equals(POIS) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setSegmentPois(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(7, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(7).getStringCellValue().equals(PROCESS_TYPE) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setSegmentProcessType(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(8, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(8).getStringCellValue().equals(TOTAL_PREVIOUS_DAY) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setSegmentTotalPreviousDays(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(9, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(9).getStringCellValue().equals(EXPIRY_TYPE) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setExpiryType(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(10, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(10).getStringCellValue().equals(DEVICE_EXPIRE_DAYS) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setSegmentDeviceExpiryDays(currentCell.getStringCellValue().replace(".0",""));
                        }
                        currentCell = currentRow.getCell(11, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(11).getStringCellValue().equals(TIME_ZONE) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setSegmentTimeZone(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(12, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(12).getStringCellValue().equals(GROUP) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setGroupId(currentCell.getStringCellValue().replace(".0",""));
                        }

                        currentCell = currentRow.getCell(13, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(13).getStringCellValue().equals(CATEGORY) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setCategory(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(14, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(14).getStringCellValue().equals(BRAND) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setBrand(currentCell.getStringCellValue());
                        }

                        currentCell = currentRow.getCell(15, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(15).getStringCellValue().equals(ALGO) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            String algoType= "";
                            if(currentCell.getStringCellValue().equalsIgnoreCase("GeoPath")) {
                                algoType = "clear channel";
                            } else if(currentCell.getStringCellValue().equalsIgnoreCase("both")) {
                                algoType = "both";
                            } else {
                                algoType = "default";
                            }
                            segmentSheetDetailDto.setAlgo(algoType);
                        }
                        currentCell = currentRow.getCell(16, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(16).getStringCellValue().equals(SEGMENT_FLIGHT) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setFlight(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(17, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(17).getStringCellValue().equals(REGION) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setRegion(currentCell.getStringCellValue());
                        }
                        //====================================Row-Data-Validate=======================================
                        BillboardDetailsDTO billboardDetailsDTO = new BillboardDetailsDTO();
                        // company id checking
                        if(segmentSheetDetailDto.getSegmentId() != null && !segmentSheetDetailDto.getSegmentId().equals("")) {
                            if(this.microServicesDetail.getSegmentsFindByIdAndStatusNot(this.bulkRequest.getToken(),segmentSheetDetailDto.getSegmentId(), Status.Delete) != null) {
                                boolean isNumeric = segmentSheetDetailDto.getSegmentId().chars().allMatch( Character::isDigit );
                                if(isNumeric) {
                                    billboardDetailsDTO.setId(Long.valueOf(segmentSheetDetailDto.getSegmentId()));
                                } else {
                                    detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with id: should not be number empty!__";
                                }
                            } else {
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with id: " + segmentSheetDetailDto.getSegmentId() + " don't exists!__";
                            }
                        }
                        // checking the name
                        if(segmentSheetDetailDto.getSegmentName() != null && !segmentSheetDetailDto.getSegmentName().equals("")) {
                            billboardDetailsDTO.setSegmentName(segmentSheetDetailDto.getSegmentName());
                        } else {
                            detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with name: should not be empty!__";
                        }
                        // description
                        if(segmentSheetDetailDto.getSegmentDescription() != null && !segmentSheetDetailDto.getSegmentDescription().equals("")) {
                            billboardDetailsDTO.setSegmentDescription(segmentSheetDetailDto.getSegmentDescription());
                        }
                        // flight detail
                        if(segmentSheetDetailDto.getSegmentFlight() != null && !segmentSheetDetailDto.getSegmentFlight().equals("")) {
                            List<SegmentScheduleDTO> segmentScheduleDTOList = new ArrayList<>();
                            if(isValidFlight(segmentSheetDetailDto.getSegmentFlight().trim())) {
                                String flights[] = segmentSheetDetailDto.getSegmentFlight().trim().split("\\],");
                                if(flights != null) {
                                    for(String flight: flights) {
                                        SegmentScheduleDTO flightDate = this.flightDate(flight);
                                        if(flightDate == null) {
                                            detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with flight: not valid!__";
                                            break;
                                        }
                                        segmentScheduleDTOList.add(flightDate);
                                    }
                                } else {
                                    SegmentScheduleDTO flightDate = this.flightDate(segmentSheetDetailDto.getSegmentFlight());
                                    if(flightDate == null) {
                                        detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with flight: not valid!__";
                                        segmentScheduleDTOList.add(flightDate);
                                    }
                                }
                                if(segmentScheduleDTOList.size() > 0) {
                                    billboardDetailsDTO.setSchedule(segmentScheduleDTOList);
                                }
                            } else {
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with Flight: pattern not valid!__";
                            }
                        } else {
                            detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with Flight: should not be empty!__";
                        }
                        // image billboard
                        if(segmentSheetDetailDto.getSegmentBillboardImageURL() != null && !segmentSheetDetailDto.getSegmentBillboardImageURL().equals("")) {
                            billboardDetailsDTO.setImgURLBillboard(segmentSheetDetailDto.getSegmentBillboardImageURL());
                            billboardDetailsDTO.setImgURL(segmentSheetDetailDto.getSegmentBillboardImageURL());
                        }
                        // geo-path-id
                        if(segmentSheetDetailDto.getSegmentGeopathId() != null && !segmentSheetDetailDto.getSegmentGeopathId().equals("")) {
                            boolean isNumeric = segmentSheetDetailDto.getSegmentGeopathId().chars().allMatch( Character::isDigit );
                            if(isNumeric) {
                                billboardDetailsDTO.setGeoPathId(Long.valueOf(segmentSheetDetailDto.getSegmentGeopathId()));
                                billboardDetailsDTO.setPanelId(Long.valueOf(segmentSheetDetailDto.getSegmentGeopathId()));
                            } else {
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with GeoPathId: should not be number empty!__";
                            }
                        } else {
                            detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with GeoPathId: should not be empty!__";
                        }
                        // pois
                        if(segmentSheetDetailDto.getSegmentPois() != null && !segmentSheetDetailDto.getSegmentPois().equals("")) {
                            this.poiSegmentDetailDto = this.microServicesDetail.getAudiencePoiFindByIdAndStatusNot(this.bulkRequest.getToken(), this.bulkRequest.getEntity().get(ID_KEY).toString(), segmentSheetDetailDto.getSegmentPois());
                            if(this.poiSegmentDetailDto == null  && this.poiSegmentDetailDto.getNotFound() != null && this.poiSegmentDetailDto.getNotFound().size() > 0) {
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with Poi: "+ this.poiSegmentDetailDto.getNotFound() +" not found!__";
                            }else {
                                String pois = this.poiSegmentDetailDto.getFound().toString();
                                billboardDetailsDTO.setPoiSelection(pois.substring(1, pois.length()-1));
                            }
                        }
                        // process type
                        if(segmentSheetDetailDto.getSegmentProcessType() != null && !segmentSheetDetailDto.getSegmentProcessType().equals("")) {
                            if(segmentSheetDetailDto.getSegmentProcessType().equals(PROCESS_TYPE_LIST[0]) || segmentSheetDetailDto.getSegmentProcessType().equals(PROCESS_TYPE_LIST[1])) {
                                if(segmentSheetDetailDto.getSegmentProcessType().equals(PROCESS_TYPE_LIST[1])) {
                                    billboardDetailsDTO.setExactDate(true);
                                } else {
                                    billboardDetailsDTO.setExactDate(false);
                                }
                            } else {
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with Process Type: should be " + PROCESS_TYPE_LIST[0] + "Or" + PROCESS_TYPE_LIST[1] + "!__";
                            }
                        } else {
                            detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with Process Type: should not be empty!__";
                        }
                        // process type if process day then use this
                        if(segmentSheetDetailDto.getSegmentProcessType() != null && segmentSheetDetailDto.getSegmentProcessType().equals(PROCESS_TYPE_LIST[0])) {
                            if(segmentSheetDetailDto.getSegmentTotalPreviousDays() != null && !segmentSheetDetailDto.getSegmentTotalPreviousDays().equals("")) {
                                boolean isNumeric = segmentSheetDetailDto.getSegmentTotalPreviousDays().chars().allMatch( Character::isDigit );
                                if(isNumeric) {
                                    Integer days = Integer.valueOf(segmentSheetDetailDto.getSegmentTotalPreviousDays());
                                    if(days < SEGMENT_EXPIRY_MAX_DAYS) {
                                        billboardDetailsDTO.setTotalDays(days);
                                    } else {
                                        detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with Previous Days : should be less then "+(SEGMENT_EXPIRY_MAX_DAYS-1)+" or equal!__";
                                    }
                                } else {
                                    detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with Previous Days : should be numeric empty!__";
                                }
                            } else {
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with Previous Days : should not be empty!__";
                            }
                        }

                        if(segmentSheetDetailDto.getExpiryType() != null && !segmentSheetDetailDto.getExpiryType().equals("")) {
                            if(segmentSheetDetailDto.getExpiryType().equals(EXPIRY_TYPE_LIST[0]) || segmentSheetDetailDto.getExpiryType().equals(EXPIRY_TYPE_LIST[1])) {
                                if(segmentSheetDetailDto.getExpiryType().equals(EXPIRY_TYPE_LIST[1])) {
                                    if(segmentSheetDetailDto.getSegmentDeviceExpiryDays() != null && !segmentSheetDetailDto.getSegmentDeviceExpiryDays().equals("")) {
                                        boolean isNumeric = segmentSheetDetailDto.getSegmentDeviceExpiryDays().chars().allMatch( Character::isDigit );
                                        if(isNumeric) {
                                            Integer days = Integer.valueOf(segmentSheetDetailDto.getSegmentDeviceExpiryDays());
                                            if(days < SEGMENT_EXPIRY_MAX_DAYS) {
                                                billboardDetailsDTO.setExpiryDays(days);
                                            } else {
                                                detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with Expire Days : should be less then "+(SEGMENT_EXPIRY_MAX_DAYS-1)+" or equal!__";
                                            }
                                        } else {
                                            detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with Expire Days : should be numeric empty!__";
                                        }
                                    } else {
                                        detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with Expire Days : should not be empty!__";
                                    }
                                } else {
                                    if(segmentSheetDetailDto.getSegmentDeviceExpiryDays() != null && !segmentSheetDetailDto.getSegmentDeviceExpiryDays().equals("")) {
                                        detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with Expire Days : should be empty When Never Expire is Selected!__";
                                    } else {
                                        billboardDetailsDTO.setExpiryDays(null);
                                    }
                                }
                            } else {
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with Expiry Type: should be " + PROCESS_TYPE_LIST[0] + " Or " + PROCESS_TYPE_LIST[1] + "!__";
                            }
                        } else {
                            detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with Expiry Type: should not be empty!__";
                        }

                      /*  if(segmentSheetDetailDto.getSegmentDeviceExpiryDays() != null && !segmentSheetDetailDto.getSegmentDeviceExpiryDays().equals("")) {
                            boolean isNumeric = segmentSheetDetailDto.getSegmentDeviceExpiryDays().chars().allMatch( Character::isDigit );
                            if(isNumeric) {
                                Integer days = Integer.valueOf(segmentSheetDetailDto.getSegmentDeviceExpiryDays());
                                if(days <= 30) {
                                    billboardDetailsDTO.setExpiryDays(days);
                                } else {
                                    detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with Expire Days : should be less then 30 or equal!__";
                                }
                            } else {
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with Expire Days : should be numeric empty!__";
                            }
                        }*/
                       /* else {
                            detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with Expire Days : should not be empty!__";
                        }*/
                        // process type if process day then use this
                        if(segmentSheetDetailDto.getSegmentTimeZone() != null && !segmentSheetDetailDto.getSegmentTimeZone().equals("")) {
                            if(segmentSheetDetailDto.getSegmentTimeZone().equals(TIME_ZONE_LIST[0]) || segmentSheetDetailDto.getSegmentTimeZone().equals(TIME_ZONE_LIST[1]) ||
                                segmentSheetDetailDto.getSegmentTimeZone().equals(TIME_ZONE_LIST[2]) || segmentSheetDetailDto.getSegmentTimeZone().equals(TIME_ZONE_LIST[3])) {
                                billboardDetailsDTO.setTimeZone(segmentSheetDetailDto.getSegmentTimeZone());
                            } else {
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with Time Zone : should be " + TIME_ZONE_LIST[0] + " " + TIME_ZONE_LIST[1] + " " + TIME_ZONE_LIST[2] + " " + TIME_ZONE_LIST[3] + "empty!__";
                            }
                        } else {
                            detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with Time Zone : should not be empty!__";
                        }

                        // Add Group Id. Null Group Also Allow.
                        if(segmentSheetDetailDto.getGroupId() != null && !segmentSheetDetailDto.getGroupId().equals("")) {
                            boolean isNumeric =segmentSheetDetailDto.getGroupId().chars().allMatch( Character::isDigit );
                            if(isNumeric) {
                                this.poiSegmentDetailDto = this.microServicesDetail.getAudiencePoiGroupFindByIdAndStatusNot(this.bulkRequest.getToken(), this.bulkRequest.getEntity().get(ID_KEY).toString(), segmentSheetDetailDto.getGroupId().toString(), 18l);
                                if(this.poiSegmentDetailDto.getNotFound() != null && this.poiSegmentDetailDto.getNotFound().size() > 0) {
                                    detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segment Group: "+ this.poiSegmentDetailDto.getNotFound() +" not found!__";
                                }else {
                                    String segmentGrp = this.poiSegmentDetailDto.getFound().toString();
                                    billboardDetailsDTO.setGroupId(Long.valueOf(segmentGrp.substring(1, segmentGrp.length()-1)));
                                }
                            } else {
                                detail += "Error in Sheet: GeoPath on row: " + (row + 1) + ", GeoPath with id: should be number empty!__";
                            }
                        }

                        if(segmentSheetDetailDto.getBrand() != null && !segmentSheetDetailDto.getBrand().equals("")) {
                            billboardDetailsDTO.setBrand(segmentSheetDetailDto.getBrand());
                        }

                        if(segmentSheetDetailDto.getCategory() != null && !segmentSheetDetailDto.getCategory().equals("")) {
                            billboardDetailsDTO.setCategory(segmentSheetDetailDto.getCategory());
                        }

                        if(segmentSheetDetailDto.getAlgo() != null && !segmentSheetDetailDto.getCategory().equals("")) {
                            billboardDetailsDTO.setAlgo(segmentSheetDetailDto.getAlgo());
                        }

                        if(segmentSheetDetailDto.getFlight() != null && !segmentSheetDetailDto.getFlight().equals("")) {
                            billboardDetailsDTO.setFlight(segmentSheetDetailDto.getFlight());
                        }

                        if(segmentSheetDetailDto.getRegion() != null && !segmentSheetDetailDto.getRegion().equals("")) {
                            billboardDetailsDTO.setRegion(segmentSheetDetailDto.getRegion());
                        }

                        // type define for segment geo-path
                        billboardDetailsDTO.setSegmentType(SegmentType.GeoPath);
                        if (this.isAgAdv.equals("1")) {
                            billboardDetailsDTO.setAgencyAdvertiserId(Long.valueOf(this.bulkRequest.getEntity().get(ID_KEY).toString()));
                            billboardDetailsDTO.setIsAgAdv(this.isAgAdv);
                        } else {
                            billboardDetailsDTO.setIsAgAdv(this.isAgAdv);
                            billboardDetailsDTO.setAgencyAdvertiserId(null);
                        }
                        // it's mean this row oky
                        if(detail.equals("")) { this.segmentGeoPathDTOList.add(billboardDetailsDTO); }
                    }
                }
            }
        } else {
            this.isValidationFailed = true;
            this.responseDTO = new ResponseDTO(false, ApiConstants.ERROR_MSG + ": Sheet Object Null", ApiCode.HTTP_400);
        }
        // final check if some
        if(!detail.equals("")) {
            this.isValidationFailed = true;
            message = "Plz Verify Segments GeoPath Some Data Wrong";
            this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, detail));
            return;
        }
    }

    private void readSegmentsGeoPathSheet() throws Exception {
        Integer row = 3;
        String message = "Saving Process Start For Segment GeoPath";
        this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, ""));
        for(BillboardDetailsDTO billboardDetailsDTO: this.segmentGeoPathDTOList) {
            ResponseDTO responseDTO = this.microServicesDetail.saveSegment(this.bulkRequest.getToken(), billboardDetailsDTO);
            message = "In Segment GeoPath sheet result for row: " + (row) + " is: " + responseDTO.getMessage();
            this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, ""));
            // verify the response of segment response and our poi list not null then process of poi's
            if (billboardDetailsDTO.getPoiSelection() != null && responseDTO.getCode().equals(ApiCode.SUCCESS)) {
                String json = this.gson.toJson(responseDTO.getContent(), LinkedHashMap.class);
                logger.info("Json Response :- " + json);
                this.gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").setDateFormat("dd/MM/yyyy").create();
                SegmentDTO segmentDTO = this.gson.fromJson(json, SegmentDTO.class);
                if (segmentDTO.getId() != null) {
                    ResponseDTO r2 = this.microServicesDetail.savePoiAndTarget(this.bulkRequest.getToken(), billboardDetailsDTO.getPoiSelection(), String.valueOf(segmentDTO.getId()));
                    message = "In Segments-GeoPath sheet result for row: " + (row) + " poi save : " + r2.getMessage() + "";
                    this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, ""));
                }
            }
            row = row+1;
        }
    }

    private void validateSegmentsOthersSheet() throws Exception {
        String detail = "";
        String message = "";
        this.segmentOtherDTORList = new ArrayList<>();
        if(this.sheet != null) {
            if (this.sheet.getLastRowNum() < 1) { // check the total row in the sheet if result zero it's mean sheet empty
                this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage("Segments Other sheet is empty", ""));
                return;
            } else { // sheet have data so validation process start
                Integer row = -1;
                Iterator<Row> iterator = this.sheet.iterator();
                Row headingRow = null;
                while (iterator.hasNext()) {
                    row = row + 1; // row start from zero
                    Row currentRow = iterator.next();
                    if (row == 1) {
                        headingRow = currentRow;
                        if (currentRow.getPhysicalNumberOfCells() != 24) {
                            this.isValidationFailed = true;
                            message = "Error in Sheet: Segments Other on row: " + (row+1) + ", Some headings missing";
                        } else {
                            if(!currentRow.getCell(0).getStringCellValue().equals(SEGMENT_ID)) {
                                this.isValidationFailed = true;
                                message = "Segments Other :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments Other on row: " + (row+1) + ", and cell should be " + SEGMENT_ID+"__";
                            }
                            if(!currentRow.getCell(1).getStringCellValue().equals(NAME)) {
                                this.isValidationFailed = true;
                                message = "Segments Other :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments Other on row: " + (row+1) + ", and cell should be " + NAME+"__";
                            }
                            if(!currentRow.getCell(2).getStringCellValue().equals(TYPE)) {
                                this.isValidationFailed = true;
                                message = "Segments Other :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments Other on row: " + (row+1) + ", and cell should be " + TYPE+"__";
                            }
                            if(!currentRow.getCell(3).getStringCellValue().equals(DESCRIPTION)) {
                                this.isValidationFailed = true;
                                message = "Segments Other :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments Other on row: " + (row+1) + ", and cell should be " + DESCRIPTION+"__";
                            }
                            if(!currentRow.getCell(4).getStringCellValue().equals(FLIGHT)) {
                                this.isValidationFailed = true;
                                message = "Segments Other :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments Other on row: " + (row+1) + ", and cell should be " + FLIGHT+"__";
                            }
                            if(!currentRow.getCell(5).getStringCellValue().equals(FULL_ADDRESS)) {
                                this.isValidationFailed = true;
                                message = "Segments Other :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments Other on row: " + (row+1) + ", and cell should be " + FULL_ADDRESS+"__";
                            }
                            if(!currentRow.getCell(6).getStringCellValue().equals(LATITUDE)) {
                                this.isValidationFailed = true;
                                message = "Segments Other :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments Other on row: " + (row+1) + ", and cell should be " + LATITUDE+"__";
                            }
                            if(!currentRow.getCell(7).getStringCellValue().equals(LONGITUDE)) {
                                this.isValidationFailed = true;
                                message = "Segments Other :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments Other on row: " + (row+1) + ", and cell should be " + LONGITUDE+"__";
                            }
                            if(!currentRow.getCell(8).getStringCellValue().equals(RADIUS)) {
                                this.isValidationFailed = true;
                                message = "Segments Other :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments Other on row: " + (row+1) + ", and cell should be " + RADIUS+"__";
                            }
                            if(!currentRow.getCell(9).getStringCellValue().equals(RADIUS_UNIT)) {
                                this.isValidationFailed = true;
                                message = "Segments Other :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments Other on row: " + (row+1) + ", and cell should be " + RADIUS_UNIT+"__";
                            }
                            if(!currentRow.getCell(10).getStringCellValue().equals(GEO_JSON)) {
                                this.isValidationFailed = true;
                                message = "Segments Other :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments Other on row: " + (row+1) + ", and cell should be " + GEO_JSON+"__";
                            }
                            if(!currentRow.getCell(11).getStringCellValue().equals(POIS)) {
                                this.isValidationFailed = true;
                                message = "Segments Other :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments Other on row: " + (row+1) + ", and cell should be " + POIS+"__";
                            }
                            if(!currentRow.getCell(12).getStringCellValue().equals(PROCESS_TYPE)) {
                                this.isValidationFailed = true;
                                message = "Segments Other :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments Other on row: " + (row+1) + ", and cell should be " + PROCESS_TYPE+"__";
                            }
                            if(!currentRow.getCell(13).getStringCellValue().equals(TOTAL_PREVIOUS_DAY)) {
                                this.isValidationFailed = true;
                                message = "Segments Other :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments Other on row: " + (row+1) + ", and cell should be " + TOTAL_PREVIOUS_DAY+"__";
                            }
                            if(!currentRow.getCell(14).getStringCellValue().equals(EXPIRY_TYPE)) {
                                this.isValidationFailed = true;
                                message = "Segments Other :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments Other on row: " + (row+1) + ", and cell should be " + EXPIRY_TYPE+"__";
                            }
                            if(!currentRow.getCell(15).getStringCellValue().equals(DEVICE_EXPIRE_DAYS)) {
                                this.isValidationFailed = true;
                                message = "Segments Other :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments Other on row: " + (row+1) + ", and cell should be " + DEVICE_EXPIRE_DAYS+"__";
                            }
                            if(!currentRow.getCell(16).getStringCellValue().equals(TIME_ZONE)) {
                                this.isValidationFailed = true;
                                message = "Segments Other :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments Other on row: " + (row+1) + ", and cell should be " + TIME_ZONE+"__";
                            }
                            if(!currentRow.getCell(17).getStringCellValue().equals(GROUP)) {
                                this.isValidationFailed = true;
                                message = "Segments Other :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments Other on row: " + (row+1) + ", and cell should be " + GROUP+"__";
                            }
                            if(!currentRow.getCell(18).getStringCellValue().equals(CATEGORY)) {
                                this.isValidationFailed = true;
                                message = "Segments Other :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments Other on row: " + (row+1) + ", and cell should be " + CATEGORY+"__";
                            }
                            if(!currentRow.getCell(19).getStringCellValue().equals(BRAND)) {
                                this.isValidationFailed = true;
                                message = "Segments Other :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments Other on row: " + (row+1) + ", and cell should be " + BRAND+"__";
                            }
                            if(!currentRow.getCell(20).getStringCellValue().equals(GEO_PATH_ID)) {
                                this.isValidationFailed = true;
                                message = "Segments Other :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments Other on row: " + (row+1) + ", and cell should be " + GEO_PATH_ID+"__";
                            }
                            if(!currentRow.getCell(21).getStringCellValue().equals(ALGO)) {
                                this.isValidationFailed = true;
                                message = "Segments Other :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments Other on row: " + (row+1) + ", and cell should be " + ALGO+"__";
                            }
                            if(!currentRow.getCell(22).getStringCellValue().equals(SEGMENT_FLIGHT)) {
                                this.isValidationFailed = true;
                                message = "Segments Other :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments Other on row: " + (row+1) + ", and cell should be " + SEGMENT_FLIGHT+"__";
                            }
                            if(!currentRow.getCell(23).getStringCellValue().equals(REGION)) {
                                this.isValidationFailed = true;
                                message = "Segments Other :- Heading are not present on proper place";
                                detail += "Error in Sheet: Segments Other on row: " + (row+1) + ", and cell should be " + REGION+"__";
                            }
                        }
                        if(this.isValidationFailed) {
                            this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, detail));
                            return;
                        }
                    } else if (row > 1) {
                        // now start validating the data
                        //====================================Row-Data-Collect=======================================
                        SegmentSheetDetailDto segmentSheetDetailDto = new SegmentSheetDetailDto();
                        Cell currentCell = currentRow.getCell(0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(0).getStringCellValue().equals(SEGMENT_ID) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setSegmentId(currentCell.getStringCellValue().replace(".0",""));
                        }
                        currentCell = currentRow.getCell(1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(1).getStringCellValue().equals(NAME) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setSegmentName(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(2, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(2).getStringCellValue().equals(TYPE) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setSegmentType(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(3, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(3).getStringCellValue().equals(DESCRIPTION) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setSegmentDescription(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(4, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(4).getStringCellValue().equals(FLIGHT) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setSegmentFlight(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(5, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(5).getStringCellValue().equals(FULL_ADDRESS) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setSegmentFullAddress(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(6, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(6).getStringCellValue().equals(LATITUDE) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setSegmentLatitude(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(7, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(7).getStringCellValue().equals(LONGITUDE) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setSegmentLongitude(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(8, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(8).getStringCellValue().equals(RADIUS) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setSegmentRadius(currentCell.getStringCellValue().replace(".0",""));
                        } else {
                            segmentSheetDetailDto.setSegmentRadius("0");
                        }

                        currentCell = currentRow.getCell(9, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(9).getStringCellValue().equals(RADIUS_UNIT) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setSegmentRadiusUnit(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(10, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(10).getStringCellValue().equals(GEO_JSON) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setSegmentGeoJsonURL(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(11, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(11).getStringCellValue().equals(POIS) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setSegmentPois(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(12, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(12).getStringCellValue().equals(PROCESS_TYPE) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setSegmentProcessType(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(13, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(13).getStringCellValue().equals(TOTAL_PREVIOUS_DAY) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setSegmentTotalPreviousDays(currentCell.getStringCellValue().replace(".0",""));
                        }

                        currentCell = currentRow.getCell(14, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(14).getStringCellValue().equals(EXPIRY_TYPE) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setExpiryType(currentCell.getStringCellValue());
                        }

                        currentCell = currentRow.getCell(15, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(15).getStringCellValue().equals(DEVICE_EXPIRE_DAYS) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setSegmentDeviceExpiryDays(currentCell.getStringCellValue().replace(".0",""));
                        }
                        currentCell = currentRow.getCell(16, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(16).getStringCellValue().equals(TIME_ZONE) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setSegmentTimeZone(currentCell.getStringCellValue());
                        }
                        currentCell= currentRow.getCell(17, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(17).getStringCellValue().equals(GROUP) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setGroupId(currentCell.getStringCellValue().replace(".0",""));
                        }
                        currentCell = currentRow.getCell(18, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(18).getStringCellValue().equals(CATEGORY) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setCategory(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(19, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(19).getStringCellValue().equals(BRAND) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setBrand(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(20, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(20).getStringCellValue().equals(GEO_PATH_ID) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setAttachGeoPathId(currentCell.getStringCellValue().replace(".0",""));
                        }
                        currentCell = currentRow.getCell(21, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(21).getStringCellValue().equals(ALGO) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            String algoType= "";
                            if(currentCell.getStringCellValue().equalsIgnoreCase("GeoPath")) {
                                algoType = "clear channel";
                            } else if(currentCell.getStringCellValue().equalsIgnoreCase("both")) {
                                algoType = "both";
                            } else {
                                algoType = "default";
                            }
                            segmentSheetDetailDto.setAlgo(algoType);
                        }
                        currentCell = currentRow.getCell(22, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(22).getStringCellValue().equals(SEGMENT_FLIGHT) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setFlight(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(23, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(23).getStringCellValue().equals(REGION) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            segmentSheetDetailDto.setRegion(currentCell.getStringCellValue());
                        }

                        //====================================Row-Data-Validate=======================================
                        BillboardDetailsDTO billboardDetailsDTO = new BillboardDetailsDTO();
                        // company id checking
                        if(segmentSheetDetailDto.getSegmentId() != null && !segmentSheetDetailDto.getSegmentId().equals("")) {
                            if(this.microServicesDetail.getSegmentsFindByIdAndStatusNot(this.bulkRequest.getToken(),segmentSheetDetailDto.getSegmentId(), Status.Delete) != null) {
                                boolean isNumeric = segmentSheetDetailDto.getSegmentId().chars().allMatch( Character::isDigit );
                                if(isNumeric) {
                                    billboardDetailsDTO.setId(Long.valueOf(segmentSheetDetailDto.getSegmentId()));
                                } else {
                                    detail += "Error in Sheet: Segments Other on row: " + (row + 1) + ", Segments with id: should not be number empty!__";
                                }
                            } else {
                                detail += "Error in Sheet: Segments Other on row: " + (row + 1) + ", Segments with id: " + segmentSheetDetailDto.getSegmentId() + " don't exists!__";
                            }
                        }
                        // checking the name
                        if(segmentSheetDetailDto.getSegmentName() != null && !segmentSheetDetailDto.getSegmentName().equals("")) {
                            billboardDetailsDTO.setSegmentName(segmentSheetDetailDto.getSegmentName());
                        } else {
                            detail += "Error in Sheet: Segments Other on row: " + (row + 1) + ", Segments with name: should not be empty!__";
                        }
                        // checking the type
                        if(segmentSheetDetailDto.getSegmentType() != null && !segmentSheetDetailDto.getSegmentType().equals("")) {
                            if(segmentSheetDetailDto.getSegmentType().equals(TYPE_LIST[0]) || segmentSheetDetailDto.getSegmentType().equals(TYPE_LIST[1])) {
                                if(segmentSheetDetailDto.getSegmentType().equals(TYPE_LIST[0])) {
                                    billboardDetailsDTO.setSegmentType(SegmentType.DriveByLocation);
                                } else {
                                    billboardDetailsDTO.setSegmentType(SegmentType.GeoFance);
                                }
                            } else {
                                detail += "Error in Sheet: Segments Other on row: " + (row + 1) + ", Segments with segment type: should be "+ TYPE_LIST[0] + " " +  TYPE_LIST[1] + "__";
                            }
                        } else {
                            detail += "Error in Sheet: Segments Other on row: " + (row + 1) + ", Segments with segment type: should not be empty!__";
                        }
                        // description
                        if(segmentSheetDetailDto.getSegmentDescription() != null && !segmentSheetDetailDto.getSegmentDescription().equals("")) {
                            billboardDetailsDTO.setSegmentDescription(segmentSheetDetailDto.getSegmentDescription());
                        }
                        // flight detail
                        if(segmentSheetDetailDto.getSegmentFlight() != null && !segmentSheetDetailDto.getSegmentFlight().equals("")) {
                            List<SegmentScheduleDTO> segmentScheduleDTOList = new ArrayList<>();
                            if(isValidFlight(segmentSheetDetailDto.getSegmentFlight().trim())) {
                                String flights[] = segmentSheetDetailDto.getSegmentFlight().trim().split("\\],");
                                if(flights != null) {
                                    for(String flight: flights) {
                                        SegmentScheduleDTO flightDate = this.flightDate(flight);
                                        if(flightDate == null) {
                                            detail += "Error in Sheet: Segments Other on row: " + (row + 1) + ", Segments with flight: not valid!__";
                                            break;
                                        }
                                        segmentScheduleDTOList.add(flightDate);
                                    }
                                } else {
                                    SegmentScheduleDTO flightDate = this.flightDate(segmentSheetDetailDto.getSegmentFlight());
                                    if(flightDate == null) {
                                        detail += "Error in Sheet: Segments Other on row: " + (row + 1) + ", Segments with flight: not valid!__";
                                        segmentScheduleDTOList.add(flightDate);
                                    }
                                }
                                if(segmentScheduleDTOList.size() > 0) {
                                    billboardDetailsDTO.setSchedule(segmentScheduleDTOList);
                                }
                            } else {
                                detail += "Error in Sheet: Segments Other on row: " + (row + 1) + ", Segments with Flight: pattern not valid!__";
                            }
                        } else {
                            detail += "Error in Sheet: Segments Other on row: " + (row + 1) + ", Segments with Flight: should not be empty!__";
                        }
                        // latitude
                        if(segmentSheetDetailDto.getSegmentLatitude() != null && !segmentSheetDetailDto.getSegmentLatitude().equals("")) {
                            if(isNumeric(segmentSheetDetailDto.getSegmentLatitude())) {
                                billboardDetailsDTO.setLatitude(Double.valueOf(segmentSheetDetailDto.getSegmentLatitude()));
                            } else {
                                detail += "Error in Sheet: Segments Other on row: " + (row + 1) + ", Segments with Latitude: should be numeric!__";
                            }
                        } else {
                            detail += "Error in Sheet: Segments Other on row: " + (row + 1) + ", Segments with Latitude: should not be empty!__";
                        }
                        // longitude
                        if(segmentSheetDetailDto.getSegmentLongitude() != null && !segmentSheetDetailDto.getSegmentLongitude().equals("")) {
                            if(isNumeric(segmentSheetDetailDto.getSegmentLongitude())) {
                                billboardDetailsDTO.setLongitude(Double.valueOf(segmentSheetDetailDto.getSegmentLongitude()));
                            } else {
                                detail += "Error in Sheet: Segments Other on row: " + (row + 1) + ", Segments with Longitude: should be numeric!__";
                            }
                        } else {
                            detail += "Error in Sheet: Segments Other on row: " + (row + 1) + ", Segments with Longitude: should not be empty!__";
                        }
                        // checking the type
                        if(segmentSheetDetailDto.getSegmentType() != null && segmentSheetDetailDto.getSegmentType().equals(TYPE_LIST[0])) {
                            if((segmentSheetDetailDto.getSegmentRadius() != null && segmentSheetDetailDto.getSegmentRadiusUnit() != null) && (!segmentSheetDetailDto.getSegmentRadius().equals("") && !segmentSheetDetailDto.getSegmentRadiusUnit().equals(""))) {
                                boolean isNumeric = segmentSheetDetailDto.getSegmentRadius().chars().allMatch( Character::isDigit );
                                if(isNumeric) {
                                    billboardDetailsDTO.setRadius(Double.valueOf(segmentSheetDetailDto.getSegmentRadius()));
                                    if(segmentSheetDetailDto.getSegmentRadiusUnit().equals("meter")) {
                                        billboardDetailsDTO.setUnit(segmentSheetDetailDto.getSegmentRadiusUnit());
                                    } else {
                                        detail += "Error in Sheet: Segments Other on row: " + (row + 1) + ", Segments with Radius Unit: should be meter empty!__";
                                    }
                                } else {
                                    detail += "Error in Sheet: Segments Other on row: " + (row + 1) + ", Segments with Radius: should be number empty!__";
                                }
                            } else {
                                detail += "Error in Sheet: Segments Other on row: " + (row + 1) + ", Segments with Drive By Location Radius and Radius Unit Not null!__";
                            }
                        } else if(segmentSheetDetailDto.getSegmentType() != null && segmentSheetDetailDto.getSegmentType().equals(TYPE_LIST[1])) {
                            if(segmentSheetDetailDto.getSegmentGeoJsonURL() != null && !segmentSheetDetailDto.getSegmentGeoJsonURL().equals("")) {
                                String url = segmentSheetDetailDto.getSegmentGeoJsonURL();
                                if ((url != null && url.length() > 0) && isValidURL(url)) {
                                    try {
                                        String geoJson = readURLToString(url);
                                        geoJson = this.microServicesDetail.getAtter(geoJson);
                                        billboardDetailsDTO.setPolyGoneArr(geoJson);
                                        billboardDetailsDTO.setGeoJson(geoJson);
                                    } catch (Exception ex) {
                                        logger.error("Error :- " + ex.getMessage());
                                        detail += "Error in Sheet: Segments-Others on row: " + (row + 1) + ", GeoJson URL is Wrong__";
                                    }
                                } else {
                                    // geo-json
                                    billboardDetailsDTO.setGeoJson(segmentSheetDetailDto.getSegmentGeoJsonURL());
                                }
                            } else {
                                detail += "Error in Sheet: Segments-Others on row: " + (row + 1) + ", Segments with Geofence+ geo json not be null!__";
                            }
                        }
                        // pois
                        if(segmentSheetDetailDto.getSegmentPois() != null && !segmentSheetDetailDto.getSegmentPois().equals("")) {
                            this.poiSegmentDetailDto = this.microServicesDetail.getAudiencePoiFindByIdAndStatusNot(this.bulkRequest.getToken(), this.bulkRequest.getEntity().get(ID_KEY).toString(), segmentSheetDetailDto.getSegmentPois());
                            if(this.poiSegmentDetailDto == null && this.poiSegmentDetailDto.getNotFound() != null && this.poiSegmentDetailDto.getNotFound().size() > 0) {
                                detail += "Error in Sheet: Segments Others on row: " + (row + 1) + ", Segments with Poi: "+ this.poiSegmentDetailDto.getNotFound() +" not found!__";
                            }else {
                                // pois => point
                                String pois = this.poiSegmentDetailDto.getFound().toString();
                                billboardDetailsDTO.setPoiSelection(pois.substring(1, pois.length()-1));
                            }
                        }
                        // process type
                        if(segmentSheetDetailDto.getSegmentProcessType() != null && !segmentSheetDetailDto.getSegmentProcessType().equals("")) {
                            if(segmentSheetDetailDto.getSegmentProcessType().equals(PROCESS_TYPE_LIST[0]) || segmentSheetDetailDto.getSegmentProcessType().equals(PROCESS_TYPE_LIST[1])) {
                                if(segmentSheetDetailDto.getSegmentProcessType().equals(PROCESS_TYPE_LIST[1])) {
                                    billboardDetailsDTO.setExactDate(true);
                                } else {
                                    billboardDetailsDTO.setExactDate(false);
                                }
                            } else {
                                detail += "Error in Sheet: Segments Others on row: " + (row + 1) + ", Segments with Process Type: should be " + PROCESS_TYPE_LIST[0] + " Or " + PROCESS_TYPE_LIST[1] + "!__";
                            }
                        }
                       /* else {
                            detail += "Error in Sheet: Segments Others on row: " + (row + 1) + ", Segments with Process Type: should not be empty!__";
                        }*/
                        // process type if process day then use this
                        if((segmentSheetDetailDto.getSegmentProcessType() != null && segmentSheetDetailDto.getSegmentProcessType().equals(PROCESS_TYPE_LIST[0]))) {
                            if(segmentSheetDetailDto.getSegmentTotalPreviousDays() != null && !segmentSheetDetailDto.getSegmentTotalPreviousDays().equals("")) {
                                boolean isNumeric = segmentSheetDetailDto.getSegmentTotalPreviousDays().chars().allMatch( Character::isDigit );
                                if(isNumeric) {
                                    Integer days = Integer.valueOf(segmentSheetDetailDto.getSegmentTotalPreviousDays());
                                    if(days < SEGMENT_EXPIRY_MAX_DAYS) {
                                        billboardDetailsDTO.setTotalDays(days);
                                    } else {
                                        detail += "Error in Sheet: Segments Others on row: " + (row + 1) + ", Segments with Previous Days : should be less then "+(SEGMENT_EXPIRY_MAX_DAYS-1)+" or equal!__";
                                    }
                                } else {
                                    detail += "Error in Sheet: Segments Others on row: " + (row + 1) + ", Segments with Previous Days : should be numeric empty!__";
                                }
                            } else {
                                detail += "Error in Sheet: Segments Others on row: " + (row + 1) + ", Segments with Previous Days : should not be empty!__";
                            }
                        }

                        if(segmentSheetDetailDto.getExpiryType() != null && !segmentSheetDetailDto.getExpiryType().equals("")) {
                            if(segmentSheetDetailDto.getExpiryType().equals(EXPIRY_TYPE_LIST[0]) || segmentSheetDetailDto.getExpiryType().equals(EXPIRY_TYPE_LIST[1])) {
                                if(segmentSheetDetailDto.getExpiryType().equals(EXPIRY_TYPE_LIST[1])) {
                                    if(segmentSheetDetailDto.getSegmentDeviceExpiryDays() != null && !segmentSheetDetailDto.getSegmentDeviceExpiryDays().equals("")) {
                                        boolean isNumeric = segmentSheetDetailDto.getSegmentDeviceExpiryDays().chars().allMatch( Character::isDigit );
                                        if(isNumeric) {
                                            Integer days = Integer.valueOf(segmentSheetDetailDto.getSegmentDeviceExpiryDays() );
                                            if(days < SEGMENT_EXPIRY_MAX_DAYS) {
                                                billboardDetailsDTO.setExpiryDays(days);
                                            } else {
                                                detail += "Error in Sheet: Segments Others on row: " + (row + 1) + ", Segments with Expire Days : should be less then "+(SEGMENT_EXPIRY_MAX_DAYS-1)+" or equal !__";
                                            }
                                        } else {
                                            detail += "Error in Sheet: Segments Others on row: " + (row + 1) + ", Segments with Expire Days : should be numeric empty! __";
                                        }
                                    } else {
                                        detail += "Error in Sheet: Segments Others on row: " + (row + 1) + ", Segments with Expire Days : should not be empty! __";
                                    }
                                } else if(segmentSheetDetailDto.getExpiryType().equals(EXPIRY_TYPE_LIST[0])){
                                    if(segmentSheetDetailDto.getSegmentDeviceExpiryDays() != null && !segmentSheetDetailDto.getSegmentDeviceExpiryDays().equals("")) {
                                        detail += "Error in Sheet: Segments Others on row: " + (row + 1) + ", Segments with Expire Days : should be empty When Never Expire is Selected!__";
                                    } else {
                                        billboardDetailsDTO.setExpiryDays(null);
                                    }
                                }
                            } else {
                                detail += "Error in Sheet: Segments Others on row: " + (row + 1) + ", Segments with Expiry Type: should be " + PROCESS_TYPE_LIST[0] + " Or " + PROCESS_TYPE_LIST[1] + "!__";
                            }
                        } else {
                            detail += "Error in Sheet: Segments Others on row: " + (row + 1) + ", Segments with Expiry Type: should not be empty!__";
                        }

                       /* if(segmentSheetDetailDto.getSegmentDeviceExpiryDays() != null && !segmentSheetDetailDto.getSegmentDeviceExpiryDays().equals("")) {
                            boolean isNumeric = segmentSheetDetailDto.getSegmentDeviceExpiryDays().chars().allMatch( Character::isDigit );
                            if(isNumeric) {
                                Integer days = Integer.valueOf(segmentSheetDetailDto.getSegmentDeviceExpiryDays());
                                if(days <= 30) {
                                    billboardDetailsDTO.setExpiryDays(days);
                                } else {
                                    detail += "Error in Sheet: Segments Others on row: " + (row + 1) + ", Segments with Expire Days : should be less then 30 or equal!__";
                                }
                            } else {
                                detail += "Error in Sheet: Segments Others on row: " + (row + 1) + ", Segments with Expire Days : should be numeric empty!__";
                            }
                        } else {
                            detail += "Error in Sheet: Segments Others on row: " + (row + 1) + ", Segments with Expire Days : should not be empty!__";
                        }*/
                        // process type if process day then use this
                        if(segmentSheetDetailDto.getSegmentTimeZone() != null && !segmentSheetDetailDto.getSegmentTimeZone().equals("")) {
                            if(segmentSheetDetailDto.getSegmentTimeZone().equals(TIME_ZONE_LIST[0]) || segmentSheetDetailDto.getSegmentTimeZone().equals(TIME_ZONE_LIST[1]) ||
                                    segmentSheetDetailDto.getSegmentTimeZone().equals(TIME_ZONE_LIST[2]) || segmentSheetDetailDto.getSegmentTimeZone().equals(TIME_ZONE_LIST[3])) {
                                billboardDetailsDTO.setTimeZone(segmentSheetDetailDto.getSegmentTimeZone());
                            } else {
                                detail += "Error in Sheet: Segments Others on row: " + (row + 1) + ", Segments with Time Zone : should be " + TIME_ZONE_LIST[0] + " " + TIME_ZONE_LIST[1] + " " + TIME_ZONE_LIST[2] + " " + TIME_ZONE_LIST[3] + "empty!__";
                            }
                        } else {
                            detail += "Error in Sheet: Segments Others on row: " + (row + 1) + ", Segments with Time Zone : should not be empty!__";
                        }

                        // Add Group
                        // Note : Null Or Empty Group Also Allow.
                        /*if(segmentSheetDetailDto.getGroupId() != null && !segmentSheetDetailDto.getGroupId().equals("")) {
                            if(new Long(segmentSheetDetailDto.getGroupId()) > 0) {
                                billboardDetailsDTO.setGroupId(new Long(segmentSheetDetailDto.getGroupId()));
                            } else {
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with Group Id should be empty greater than Zero.";
                            }
                        }*/
                        if(segmentSheetDetailDto.getGroupId() != null && !segmentSheetDetailDto.getGroupId().equals("")) {
                            boolean isNumeric =segmentSheetDetailDto.getGroupId().chars().allMatch( Character::isDigit );
                            if(isNumeric) {
                                this.poiSegmentDetailDto = this.microServicesDetail.getAudiencePoiGroupFindByIdAndStatusNot(this.bulkRequest.getToken(), this.bulkRequest.getEntity().get(ID_KEY).toString(), segmentSheetDetailDto.getGroupId(), 18l);
                                if(this.poiSegmentDetailDto.getNotFound() != null && this.poiSegmentDetailDto.getNotFound().size() > 0) {
                                    detail += "Error in Sheet: Segments Others on row: " + (row + 1) + ", Segment Group: "+ this.poiSegmentDetailDto.getNotFound() +" not found!__";
                                }else {
                                    String segmentGrp = this.poiSegmentDetailDto.getFound().toString();
                                    billboardDetailsDTO.setGroupId(Long.valueOf(segmentGrp.substring(1, segmentGrp.length()-1)));
                                }
                            } else {
                                detail += "Error in Sheet: Others on row: " + (row + 1) + ", Poi with id: should be number empty!__";
                            }
                           /* if(new Long(poiSheetSheetDto.getGroupId()) > 0) {
                                poiDTO.setGroupId(new Long(poiSheetSheetDto.getGroupId()));
                            } else {
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with Group Id should be empty greater than Zero.";
                            }*/
                        }

                        if(segmentSheetDetailDto.getBrand() != null && !segmentSheetDetailDto.getBrand().equals("")) {
                            billboardDetailsDTO.setBrand(segmentSheetDetailDto.getBrand());
                        }

                        if(segmentSheetDetailDto.getAttachGeoPathId() != null && !segmentSheetDetailDto.getAttachGeoPathId().equals("")) {
                            boolean isNumeric = segmentSheetDetailDto.getAttachGeoPathId().chars().allMatch( Character::isDigit );
                            if(isNumeric) {
                                billboardDetailsDTO.setAttachGeoPathId(new Long(segmentSheetDetailDto.getAttachGeoPathId().toString()));
                            }
                        }

                        if(segmentSheetDetailDto.getCategory() != null && !segmentSheetDetailDto.getCategory().equals("")) {
                            billboardDetailsDTO.setCategory(segmentSheetDetailDto.getCategory());
                        }

                        if(segmentSheetDetailDto.getAlgo() != null && !segmentSheetDetailDto.getCategory().equals("")) {
                            billboardDetailsDTO.setAlgo(segmentSheetDetailDto.getAlgo());
                        }

                        if (this.isAgAdv.equals("1")) {
                            billboardDetailsDTO.setAgencyAdvertiserId(Long.valueOf(this.bulkRequest.getEntity().get(ID_KEY).toString()));
                            billboardDetailsDTO.setIsAgAdv(this.isAgAdv);
                        } else {
                            billboardDetailsDTO.setIsAgAdv(this.isAgAdv);
                            billboardDetailsDTO.setAgencyAdvertiserId(null);
                        }
                        // it's mean this row oky
                        if(detail.equals("")) { this.segmentOtherDTORList.add(billboardDetailsDTO); }
                    }
                }
            }
        } else {
            this.isValidationFailed = true;
            this.responseDTO = new ResponseDTO(false, ApiConstants.ERROR_MSG + ": Sheet Object Null", ApiCode.HTTP_400);
        }
        // final check if some
        if(!detail.equals("")) {
            this.isValidationFailed = true;
            message = "Plz Verify Segments Other Some Data Wrong";
            this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, detail));
            return;
        }
    }

    private void readSegmentsOthersSheet() throws Exception {
        Integer row = 3;
        String message = "Saving Process Start For Segments Other";
        this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, ""));
        for(BillboardDetailsDTO billboardDetailsDTO: this.segmentOtherDTORList) {
            ResponseDTO responseDTO = this.microServicesDetail.saveSegment(this.bulkRequest.getToken(), billboardDetailsDTO);
            message = "In Segments Other sheet result for row: " + (row) + " is: " + responseDTO.getMessage();
            this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, ""));
            // verify the response of segment response and our poi list not null then process of poi's
            if (billboardDetailsDTO.getPoiSelection() != null && responseDTO.getCode().equals(ApiCode.SUCCESS)) {
                String json = this.gson.toJson(responseDTO.getContent(), LinkedHashMap.class);
                logger.info("Json Response :- " + json);
                this.gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").setDateFormat("dd/MM/yyyy").create();
                SegmentDTO segmentDTO = this.gson.fromJson(json, SegmentDTO.class);
                if (segmentDTO.getId() != null) {
                    ResponseDTO r2 = this.microServicesDetail.savePoiAndTarget(this.bulkRequest.getToken(), billboardDetailsDTO.getPoiSelection(), String.valueOf(segmentDTO.getId()));
                    message = "In Segments Other sheet result for row: " + (row) + " poi save : " + r2.getMessage() + "";
                    this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, ""));
                }
            }
            row = row+1;
        }
    }

    private void validatePoiSheet() throws Exception {
        String detail = "";
        String message = "";
        this.poiDTOS = new ArrayList<>();
        if(this.sheet != null) {
            if (this.sheet.getLastRowNum() < 1) { // check the total row in the sheet if result zero it's mean sheet empty
                this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage("Poi sheet is empty", ""));
                return;
            } else { // sheet have data so validation process start
                Integer row = -1;
                Iterator<Row> iterator = this.sheet.iterator();
                Row headingRow = null;
                while (iterator.hasNext()) {
                    row = row + 1; // row start from zero
                    Row currentRow = iterator.next();
                    if (row == 1) {
                        headingRow = currentRow;
                        if (currentRow.getPhysicalNumberOfCells() != 11) {
                            this.isValidationFailed = true;
                            message = "Error in Sheet: Poi on row: " + (row+1) + ", Some headings missing";
                        } else {
                            if(!currentRow.getCell(0).getStringCellValue().equals(POI_ID)) {
                                this.isValidationFailed = true;
                                message = "Poi :- Heading are not present on proper place";
                                detail += "Error in Sheet: Poi on row: " + (row+1) + ", and cell should be " + POI_ID+"__";
                            }
                            if(!currentRow.getCell(1).getStringCellValue().equals(NAME)) {
                                this.isValidationFailed = true;
                                message = "Poi :- Heading are not present on proper place";
                                detail += "Error in Sheet: Poi on row: " + (row+1) + ", and cell should be " + NAME+"__";
                            }
                            if(!currentRow.getCell(2).getStringCellValue().equals(FULL_ADDRESS)) {
                                this.isValidationFailed = true;
                                message = "Poi :- Heading are not present on proper place";
                                detail += "Error in Sheet: Poi on row: " + (row+1) + ", and cell should be " + FULL_ADDRESS+"__";
                            }
                            if(!currentRow.getCell(3).getStringCellValue().equals(CITY)) {
                                this.isValidationFailed = true;
                                message = "Poi :- Heading are not present on proper place";
                                detail += "Error in Sheet: Poi on row: " + (row+1) + ", and cell should be " + CITY+"__";
                            }
                            if(!currentRow.getCell(4).getStringCellValue().equals(ZIP_CODE)) {
                                this.isValidationFailed = true;
                                message = "Poi :- Heading are not present on proper place";
                                detail += "Error in Sheet: Poi on row: " + (row+1) + ", and cell should be " + ZIP_CODE+"__";
                            }
                            if(!currentRow.getCell(5).getStringCellValue().equals(LATITUDE)) {
                                this.isValidationFailed = true;
                                message = "Poi :- Heading are not present on proper place";
                                detail += "Error in Sheet: Poi on row: " + (row+1) + ", and cell should be " + LATITUDE+"__";
                            }
                            if(!currentRow.getCell(6).getStringCellValue().equals(LONGITUDE)) {
                                this.isValidationFailed = true;
                                message = "Poi :- Heading are not present on proper place";
                                detail += "Error in Sheet: Poi on row: " + (row+1) + ", and cell should be " + LONGITUDE+"__";
                            }
                            if(!currentRow.getCell(7).getStringCellValue().equals(RADIUS)) {
                                this.isValidationFailed = true;
                                message = "Poi :- Heading are not present on proper place";
                                detail += "Error in Sheet: Poi on row: " + (row+1) + ", and cell should be " + RADIUS+"__";
                            }
                            if(!currentRow.getCell(8).getStringCellValue().equals(RADIUS_UNIT)) {
                                this.isValidationFailed = true;
                                message = "Poi :- Heading are not present on proper place";
                                detail += "Error in Sheet: Poi on row: " + (row+1) + ", and cell should be " + RADIUS_UNIT+"__";
                            }
                            if(!currentRow.getCell(9).getStringCellValue().equals(GEO_JSON)) {
                                this.isValidationFailed = true;
                                message = "Poi :- Heading are not present on proper place";
                                detail += "Error in Sheet: Poi on row: " + (row+1) + ", and cell should be " + GEO_JSON+"__";
                            }

                            if(!currentRow.getCell(10).getStringCellValue().equals(GROUP)) {
                                this.isValidationFailed = true;
                                message = "Poi :- Heading are not present on proper place";
                                detail += "Error in Sheet: Poi on row: " + (row+1) + ", and cell should be " + GROUP+"__";
                            }
                        }
                        if(this.isValidationFailed) {
                            this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, detail));
                            return;
                        }
                    } else if (row > 1) {
                        PoiSheetSheetDto poiSheetSheetDto = new PoiSheetSheetDto();
                        Cell currentCell = currentRow.getCell(0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(0).getStringCellValue().equals(POI_ID) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            poiSheetSheetDto.setPoiId(currentCell.getStringCellValue().replace(".0",""));
                        }
                        currentCell = currentRow.getCell(1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(1).getStringCellValue().equals(NAME) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            poiSheetSheetDto.setPoiName(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(2, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(2).getStringCellValue().equals(FULL_ADDRESS) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            poiSheetSheetDto.setPoiFullAddress(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(3, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(3).getStringCellValue().equals(CITY) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            poiSheetSheetDto.setPoiCity(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(4, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(4).getStringCellValue().equals(ZIP_CODE) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            poiSheetSheetDto.setPoiZipCode(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(5, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(5).getStringCellValue().equals(LATITUDE) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            poiSheetSheetDto.setPoiLatitude(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(6, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(6).getStringCellValue().equals(LONGITUDE) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            poiSheetSheetDto.setPoiLongitude(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(7, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(7).getStringCellValue().equals(RADIUS) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            poiSheetSheetDto.setPoiRadius(currentCell.getStringCellValue().replace(".0",""));
                        }
                        currentCell = currentRow.getCell(8, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(8).getStringCellValue().equals(RADIUS_UNIT) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            poiSheetSheetDto.setPoiRadiusUnit(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(9, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(9).getStringCellValue().equals(GEO_JSON) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            poiSheetSheetDto.setPoiGeoJsonUrl(currentCell.getStringCellValue());
                        }

                        currentCell = currentRow.getCell(10, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(10).getStringCellValue().equals(GROUP) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            poiSheetSheetDto.setGroupId(currentCell.getStringCellValue().replace(".0",""));
                        }

                        //====================================Row-Data-Validate=======================================
                        PoiDTO poiDTO = new PoiDTO();
                        // poi id
                        if(poiSheetSheetDto.getPoiId() != null && !poiSheetSheetDto.getPoiId().equals("")) {
                            boolean isNumeric = poiSheetSheetDto.getPoiId().chars().allMatch( Character::isDigit );
                            if(isNumeric) {
                                this.poiSegmentDetailDto = this.microServicesDetail.getAudiencePoiFindByIdAndStatusNot(this.bulkRequest.getToken(), this.bulkRequest.getEntity().get(ID_KEY).toString(), poiSheetSheetDto.getPoiId());
                                if(this.poiSegmentDetailDto.getNotFound() != null && this.poiSegmentDetailDto.getNotFound().size() > 0) {
                                    detail += "Error in Sheet: Poi on row: " + (row + 1) + ", Poi : "+ this.poiSegmentDetailDto.getNotFound() +" not found!__";
                                }else {
                                    String pois = this.poiSegmentDetailDto.getFound().toString();
                                    poiDTO.setId(Long.valueOf(pois.substring(1, pois.length()-1)));
                                }
                            } else {
                                detail += "Error in Sheet: Poi on row: " + (row + 1) + ", Poi with id: should be number empty!__";
                            }
                        }
                        // poi name
                        if(poiSheetSheetDto.getPoiName() != null && !poiSheetSheetDto.getPoiName().equals("")) {
                            poiDTO.setPoiName(poiSheetSheetDto.getPoiName());
                            poiDTO.setName(poiSheetSheetDto.getPoiName());
                        } else {
                            detail += "Error in Sheet: Poi on row: " + (row + 1) + ", Poi with name: should not be empty!__";
                        }
                        // poi full address
                        if(poiSheetSheetDto.getPoiFullAddress() != null && !poiSheetSheetDto.getPoiFullAddress().equals("")) {
                            poiDTO.setAddress(poiSheetSheetDto.getPoiFullAddress()); // adding on both
                            poiDTO.setFullAddress(poiSheetSheetDto.getPoiFullAddress());
                        }
                        // poi full address
                        if(poiSheetSheetDto.getPoiCity() != null && !poiSheetSheetDto.getPoiCity().equals("")) {
                            poiDTO.setCity(poiSheetSheetDto.getPoiCity());
                        }
                        // poi zip code
                        if(poiSheetSheetDto.getPoiZipCode() != null && !poiSheetSheetDto.getPoiZipCode().equals("")) {
                            poiDTO.setZip(poiSheetSheetDto.getPoiZipCode());
                        }
                        // poi lattitude
                        if(poiSheetSheetDto.getPoiLatitude() != null && !poiSheetSheetDto.getPoiLatitude().equals("")) {
                            if(isNumeric(poiSheetSheetDto.getPoiLatitude())) {
                                poiDTO.setLatitude(Double.valueOf(poiSheetSheetDto.getPoiLatitude()));
                                poiDTO.setLat(Double.valueOf(poiSheetSheetDto.getPoiLatitude()));
                            } else {
                                detail += "Error in Sheet: Poi on row: " + (row + 1) + ", Poi with latitude: should be number empty!__";
                            }
                        } else {
                            detail += "Error in Sheet: Poi on row: " + (row + 1) + ", Poi with latitude: should not be empty!__";
                        }
                        // poi longitude
                        if(poiSheetSheetDto.getPoiLongitude() != null && !poiSheetSheetDto.getPoiLongitude().equals("")) {
                            if(isNumeric(poiSheetSheetDto.getPoiLongitude())) {
                                poiDTO.setLongitude(Double.valueOf(poiSheetSheetDto.getPoiLongitude()));
                                poiDTO.setLng(Double.valueOf(poiSheetSheetDto.getPoiLongitude()));
                            } else {
                                detail += "Error in Sheet: Poi on row: " + (row + 1) + ", Poi with longitude: should be number empty!__";
                            }
                        } else {
                            detail += "Error in Sheet: Poi on row: " + (row + 1) + ", Poi with longitude: should not be empty!__";
                        }
                        // poi radius
                        if((poiSheetSheetDto.getPoiRadius() != null  && !poiSheetSheetDto.getPoiRadius().equals("")) && (poiSheetSheetDto.getPoiRadiusUnit() != null  && !poiSheetSheetDto.getPoiRadiusUnit().equals(""))) {
                            if(isNumeric(poiSheetSheetDto.getPoiRadius())) {
                                poiDTO.setRadius(Double.valueOf(poiSheetSheetDto.getPoiRadius()));
                                if(poiSheetSheetDto.getPoiRadiusUnit().equals("meter")) {
                                    poiDTO.setUnit(poiSheetSheetDto.getPoiRadiusUnit());
                                } else {
                                    detail += "Error in Sheet: Poi on row: " + (row + 1) + ", Poi with Radius Unit: should be meter empty!__";
                                }
                                // it's mean it's geo-json
                                if(poiSheetSheetDto.getPoiRadius().equals("0") && poiSheetSheetDto.getPoiRadiusUnit().equals("meter")) {
                                    if(poiSheetSheetDto.getPoiGeoJsonUrl() != null && !poiSheetSheetDto.getPoiGeoJsonUrl().equals("")) {
                                        String url = poiSheetSheetDto.getPoiGeoJsonUrl();
                                        if ((url != null && url.length() > 0) && isValidURL(url)) {
                                            try {
                                                String geoJso = readURLToString(url);
                                                geoJso = this.microServicesDetail.getAtter(geoJso);
                                                poiDTO.setPolyGoneArr(geoJso);
                                                poiDTO.setGeoJson(geoJso);
                                            } catch (Exception ex) {
                                                logger.error("Error :- " + ex.getMessage());
                                                detail += "Error in Sheet: Poi on row: " + (row + 1) + ", GeoJson URL is Wrong__";
                                            }
                                        }
                                    } else {
                                        detail += "Error in Sheet: Poi on row: " + (row + 1) + ", Poi with GeoJson: should be number empty!__";
                                    }
                                }
                            } else {
                                detail += "Error in Sheet: Poi on row: " + (row + 1) + ", Poi with Radius: should be number empty!__";
                            }
                        } else {
                            // meter and radius not there then check the url and set the default meter and 0.0
                            if(poiSheetSheetDto.getPoiGeoJsonUrl() != null && !poiSheetSheetDto.getPoiGeoJsonUrl().equals("")) {
                                String url = poiSheetSheetDto.getPoiGeoJsonUrl();
                                if ((url != null && url.length() > 0) && isValidURL(url)) {
                                    try {
                                        String geoJso = readURLToString(url);
                                        poiDTO.setPolyGoneArr(geoJso);
                                        poiDTO.setGeoJson(geoJso);
                                        poiDTO.setUnit("meter");
                                        poiDTO.setRadius(0.0);
                                    } catch (Exception ex) {
                                        logger.error("Error :- " + ex.getMessage());
                                        detail += "Error in Sheet: Poi on row: " + (row + 1) + ", GeoJson URL is Wrong__";
                                    }
                                }
                            } else {
                                detail += "Error in Sheet: Poi on row: " + (row + 1) + ", Poi with Radius & Radius Unit: need when geo json url not there: !__";
                                detail += "Error in Sheet: Poi on row: " + (row + 1) + ", Poi with Geo Json: if json there then no need radius & radius unit __";
                            }
                        }

                        // Add Group Id. Null Group Also Allow.
                        if(poiSheetSheetDto.getGroupId() != null && !poiSheetSheetDto.getGroupId().equals("")) {
                            boolean isNumeric =poiSheetSheetDto.getGroupId().chars().allMatch( Character::isDigit );
                            if(isNumeric) {
                                this.poiSegmentDetailDto = this.microServicesDetail.getAudiencePoiGroupFindByIdAndStatusNot(this.bulkRequest.getToken(), this.bulkRequest.getEntity().get(ID_KEY).toString(), poiSheetSheetDto.getGroupId(), 25l);
                                if(this.poiSegmentDetailDto.getNotFound() != null && this.poiSegmentDetailDto.getNotFound().size() > 0) {
                                    detail += "Error in Sheet: Poi on row: " + (row + 1) + ", Poi Group: "+ this.poiSegmentDetailDto.getNotFound() +" not found!__";
                                }else {
                                    String pois = this.poiSegmentDetailDto.getFound().toString();
                                    poiDTO.setGroupId(Long.valueOf(pois.substring(1, pois.length()-1)));
                                }
                            } else {
                                detail += "Error in Sheet: Poi on row: " + (row + 1) + ", Poi with id: should be number empty!__";
                            }
                           /* if(new Long(poiSheetSheetDto.getGroupId()) > 0) {
                                poiDTO.setGroupId(new Long(poiSheetSheetDto.getGroupId()));
                            } else {
                                detail += "Error in Sheet: Segments GeoPath on row: " + (row + 1) + ", Segments with Group Id should be empty greater than Zero.";
                            }*/
                        }

                        if (this.isAgAdv.equals("1")) {
                            poiDTO.setAgencyAdvertiserId(Long.valueOf(this.bulkRequest.getEntity().get(ID_KEY).toString()));
                            poiDTO.setIsAgAdv(this.isAgAdv);
                        } else {
                            poiDTO.setIsAgAdv(this.isAgAdv);
                            poiDTO.setAgencyAdvertiserId(null);
                        }
                        // it's mean this row oky
                        if(detail.equals("")) { this.poiDTOS.add(poiDTO); }
                    }
                }
            }
        } else {
            this.isValidationFailed = true;
            this.responseDTO = new ResponseDTO(false, ApiConstants.ERROR_MSG + ": Sheet Object Null", ApiCode.HTTP_400);
        }
        // final check if some
        if(!detail.equals("")) {
            this.isValidationFailed = true;
            message = "Plz Verify Pois Some Data Wrong";
            this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, detail));
            return;
        }
    }

    private void readPoiSheet() throws Exception {
        Integer row = 3;
        String message = "Saving Process Start For Pois";
        this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, ""));
        for(PoiDTO poiDTO: this.poiDTOS) {
            ResponseDTO responseDTO = this.microServicesDetail.savePoi(this.bulkRequest.getToken(), poiDTO);
            message = "In Pois sheet result for row: " + (row) + " is: " + responseDTO.getMessage();
            this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, ""));
            row = row+1;
        }
    }

    private void validateCampaignsSheet() throws Exception {
        String detail = "";
        String message = "";
        this.campaignDTOList = new ArrayList<>();
        if(this.sheet != null) {
            if (this.sheet.getLastRowNum() < 1) { // check the total row in the sheet if result zero it's mean sheet empty
                this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage("Campaign sheet is empty", ""));
                return;
            } else { // sheet have data so validation process start
                Integer row = -1;
                Iterator<Row> iterator = this.sheet.iterator();
                Row headingRow = null;
                while (iterator.hasNext()) {
                    row = row + 1; // row start from zero
                    Row currentRow = iterator.next();
                    if (row == 1) {
                        headingRow = currentRow;
                        if (currentRow.getPhysicalNumberOfCells() != 11) {
                            this.isValidationFailed = true;
                            message = "Error in Sheet: Campaign on row: " + (row+1) + ", Some headings missing";
                        } else {
                            if(!currentRow.getCell(0).getStringCellValue().equals(CAMPAIGN_ID)) {
                                this.isValidationFailed = true;
                                message = "Campaign :- Heading are not present on proper place";
                                detail += "Error in Sheet: Campaign on row: " + (row+1) + ", and cell should be " + CAMPAIGN_ID+"__";
                            }
                            if(!currentRow.getCell(1).getStringCellValue().equals(NAME)) {
                                this.isValidationFailed = true;
                                message = "Campaign :- Heading are not present on proper place";
                                detail += "Error in Sheet: Campaign on row: " + (row+1) + ", and cell should be " + NAME+"__";
                            }
                            if(!currentRow.getCell(2).getStringCellValue().equals(STATE)) {
                                this.isValidationFailed = true;
                                message = "Campaign :- Heading are not present on proper place";
                                detail += "Error in Sheet: Campaign on row: " + (row+1) + ", and cell should be " + STATE+"__";
                            }
                            if(!currentRow.getCell(3).getStringCellValue().equals(TYPE)) {
                                this.isValidationFailed = true;
                                message = "Campaign :- Heading are not present on proper place";
                                detail += "Error in Sheet: Campaign on row: " + (row+1) + ", and cell should be " + TYPE+"__";
                            }
                            if(!currentRow.getCell(4).getStringCellValue().equals(FB_OBJECTIVE)) {
                                this.isValidationFailed = true;
                                message = "Campaign :- Heading are not present on proper place";
                                detail += "Error in Sheet: Campaign on row: " + (row+1) + ", and cell should be " + FB_OBJECTIVE+"__";
                            }
                            if(!currentRow.getCell(5).getStringCellValue().equals(BUDGET_TYPE)) {
                                this.isValidationFailed = true;
                                message = "Campaign :- Heading are not present on proper place";
                                detail += "Error in Sheet: Campaign on row: " + (row+1) + ", and cell should be " + BUDGET_TYPE+"__";
                            }
                            if(!currentRow.getCell(6).getStringCellValue().equals(BILLING_PERIOD)) {
                                this.isValidationFailed = true;
                                message = "Campaign :- Heading are not present on proper place";
                                detail += "Error in Sheet: Campaign on row: " + (row+1) + ", and cell should be " + BILLING_PERIOD+"__";
                            }
                            if(!currentRow.getCell(7).getStringCellValue().equals(BUDGET)) {
                                this.isValidationFailed = true;
                                message = "Campaign :- Heading are not present on proper place";
                                detail += "Error in Sheet: Campaign on row: " + (row+1) + ", and cell should be " + BUDGET+"__";
                            }
                            if(!currentRow.getCell(8).getStringCellValue().equals(START_DATE)) {
                                this.isValidationFailed = true;
                                message = "Campaign :- Heading are not present on proper place";
                                detail += "Error in Sheet: Campaign on row: " + (row+1) + ", and cell should be " + START_DATE+"__";
                            }
                            if(!currentRow.getCell(9).getStringCellValue().equals(END_DATE)) {
                                this.isValidationFailed = true;
                                message = "Campaign :- Heading are not present on proper place";
                                detail += "Error in Sheet: Campaign on row: " + (row+1) + ", and cell should be " + END_DATE+"__";
                            }
                            if(!currentRow.getCell(10).getStringCellValue().equals(LINE_ITEM)) {
                                this.isValidationFailed = true;
                                message = "Campaign :- Heading are not present on proper place";
                                detail += "Error in Sheet: Campaign on row: " + (row+1) + ", and cell should be " + LINE_ITEM+"__";
                            }
                        }
                        if(this.isValidationFailed) {
                            this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, detail));
                            return;
                        }
                    } else if (row > 1) {
                        CampaignsSheetDto campaignsSheetDto = new CampaignsSheetDto();
                        Cell currentCell = currentRow.getCell(0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(0).getStringCellValue().equals(CAMPAIGN_ID) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            campaignsSheetDto.setCampaignId(currentCell.getStringCellValue().replace(".0",""));
                        }
                        currentCell = currentRow.getCell(1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(1).getStringCellValue().equals(NAME) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            campaignsSheetDto.setCampaignName(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(2, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(2).getStringCellValue().equals(STATE) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            campaignsSheetDto.setCampaignState(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(3, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(3).getStringCellValue().equals(TYPE) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            campaignsSheetDto.setCampaignType(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(4, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(4).getStringCellValue().equals(FB_OBJECTIVE) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            campaignsSheetDto.setCampaignFBObjective(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(5, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(5).getStringCellValue().equals(BUDGET_TYPE) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            campaignsSheetDto.setCampaignBudgetType(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(6, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(6).getStringCellValue().equals(BILLING_PERIOD) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            campaignsSheetDto.setCampaignBillingPeriod(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(7, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(7).getStringCellValue().equals(BUDGET) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            campaignsSheetDto.setCampaignBudget(currentCell.getStringCellValue().replace(".0",""));
                        }
                        currentCell = currentRow.getCell(8, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(8).getStringCellValue().equals(START_DATE) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            campaignsSheetDto.setCampaignStartDate(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(9, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(9).getStringCellValue().equals(END_DATE) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            campaignsSheetDto.setCampaignEndDate(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(10, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(10).getStringCellValue().equals(LINE_ITEM) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            campaignsSheetDto.setCampaignLineItems(currentCell.getStringCellValue());
                        }

                        //====================================Row-Data-Validate=======================================
                        CampaignDTO campaignDTO = new CampaignDTO();
                        // campaign id
                        if(campaignsSheetDto.getCampaignId() != null && !campaignsSheetDto.getCampaignId().equals("")) {
                            if(this.microServicesDetail.getCampFindByIdAndStatusNot(this.bulkRequest.getToken(),campaignsSheetDto.getCampaignId(), Status.Delete) != null) {
                                boolean isNumeric = campaignsSheetDto.getCampaignId().chars().allMatch( Character::isDigit );
                                if(isNumeric) {
                                    campaignDTO.setId(Long.valueOf(campaignsSheetDto.getCampaignId()));
                                } else {
                                    detail += "Error in Sheet: Campaign on row: " + (row + 1) + ", Campaign with id: should not be number empty!__";
                                }
                            } else {
                                detail += "Error in Sheet: Campaign Other on row: " + (row + 1) + ", Campaign with id: " + campaignsSheetDto.getCampaignId() + " don't exists!__";
                            }
                        }
                        // campaign name
                        if(campaignsSheetDto.getCampaignName() != null && !campaignsSheetDto.getCampaignName().equals("")) {
                            campaignDTO.setName(campaignsSheetDto.getCampaignName());
                        } else {
                            detail += "Error in Sheet: Campaign on row: " + (row + 1) + ", Campaign with name: should not be empty!__";
                        }
                        // campaign state
                        if(campaignsSheetDto.getCampaignState() != null && !campaignsSheetDto.getCampaignState().equals("")) {
                            if(campaignsSheetDto.getCampaignState().equals(CAMPAIGNS_STATE[0]) || campaignsSheetDto.getCampaignState().equals(CAMPAIGNS_STATE[1])) {
                                campaignDTO.setStatus(campaignsSheetDto.getCampaignState());
                            } else {
                                detail += "Error in Sheet: Campaign on row: " + (row + 1) + ", Campaign with state: Active or Inactive!__";
                            }
                        } else {
                            detail += "Error in Sheet: Campaign on row: " + (row + 1) + ", Campaign with state: should not be empty!__";
                        }
                        // campaign Type
                        if(campaignsSheetDto.getCampaignType() != null && !campaignsSheetDto.getCampaignType().equals("")) {
                            if(campaignsSheetDto.getCampaignType().equals(CAMPAIGNS_TYPE[0]) || campaignsSheetDto.getCampaignType().equals(CAMPAIGNS_TYPE[1])) {
                                if (campaignsSheetDto.getCampaignType().equals(CAMPAIGNS_TYPE[1])) {
                                    campaignDTO.setType(ApiConstants.FACEBOOK);
                                } else {
                                    campaignDTO.setType(ApiConstants.WEB);
                                }
                            } else {
                                detail += "Error in Sheet: Campaign on row: " + (row + 1) + ", Campaign with type: Web or Facbook!__";
                            }
                        } else {
                            detail += "Error in Sheet: Campaign on row: " + (row + 1) + ", Campaign with type: should not be empty!__";
                        }

                        if(campaignsSheetDto.getCampaignType() != null) {
                            // for facebook
                            if(campaignsSheetDto.getCampaignType().equals(CAMPAIGNS_TYPE[1]) && !campaignsSheetDto.getCampaignType().equals("")) {
                                // campain object for fb
                                if(campaignsSheetDto.getCampaignFBObjective() != null && campaignsSheetDto.getCampaignFBObjective().equals(FB_OBJECTIVE_TYPE[0]) || campaignsSheetDto.getCampaignFBObjective().equals(FB_OBJECTIVE_TYPE[1])) {
                                    campaignDTO.setObjective(campaignsSheetDto.getCampaignType());
                                } else {
                                    detail += "Error in Sheet: Campaign on row: " + (row + 1) + ", Campaign with FB Objective: should not be empty (Brand Awareness & Traffic)!__";
                                }
                                // fb budget
                                if(campaignsSheetDto.getCampaignBudget() != null && !campaignsSheetDto.getCampaignBudget().equals("")) {
                                    boolean isNumeric = campaignsSheetDto.getCampaignBudget().chars().allMatch( Character::isDigit );
                                    if(isNumeric) {
                                        campaignDTO.setBudget(Double.valueOf(campaignsSheetDto.getCampaignBudget()));
                                    } else {
                                        detail += "Error in Sheet: Campaign on row: " + (row + 1) + ", Campaign with Budget: should not be number empty!__";
                                    }
                                } else {
                                    detail += "Error in Sheet: Campaign on row: " + (row + 1) + ", Campaign with Budget: should not be empty!__";
                                }
                                // campaign date
                                if(campaignsSheetDto.getCampaignStartDate() != null && !campaignsSheetDto.getCampaignStartDate().equals("")) {
                                    if(isValidDate(campaignsSheetDto.getCampaignStartDate())) {
                                        Date parsedDate = this.incrementDaysOne(this.dateFormat.parse(campaignsSheetDto.getCampaignStartDate()));
                                        Timestamp timestamp = new java.sql.Timestamp(parsedDate.getTime());
                                        campaignDTO.setStartDate(timestamp);
                                        campaignDTO.setStartTime("00:00:00");
                                        campaignDTO.setEndTime("23:59:59");;
                                    } else {
                                        detail += "Error in Sheet: Campaign on row: " + (row + 1) + ", Campaign with Start Date: not valid should be (yyyy-MM-dd) pattern!__";
                                    }
                                } else {
                                    detail += "Error in Sheet: Campaign on row: " + (row + 1) + ", Campaign with Start Date: should not be empty!__";
                                }
                                // face book case date
                            } else if(campaignsSheetDto.getCampaignType().equals(CAMPAIGNS_TYPE[0]) && !campaignsSheetDto.getCampaignType().equals("")) { // for web
                                if(campaignsSheetDto.getCampaignBudgetType() != null && !campaignsSheetDto.getCampaignBudgetType().equals("")) {
                                    if(campaignsSheetDto.getCampaignBudgetType().equals(BUDGET_TYPE_LIST[0]) || campaignsSheetDto.getCampaignBudgetType().equals(BUDGET_TYPE_LIST[1])) {
                                        if(campaignsSheetDto.getCampaignBudgetType().equals(BUDGET_TYPE_LIST[0])) {
                                            campaignDTO.setUnlimited(false);
                                            if(campaignsSheetDto.getCampaignBudget() != null && !campaignsSheetDto.getCampaignBudget().equals("")) {
                                                boolean isNumeric = campaignsSheetDto.getCampaignBudget().chars().allMatch( Character::isDigit );
                                                if(isNumeric) {
                                                    campaignDTO.setBudget(Double.valueOf(campaignsSheetDto.getCampaignBudget()));
                                                } else {
                                                    detail += "Error in Sheet: Campaign on row: " + (row + 1) + ", Campaign with Budget: should not be number empty!__";
                                                }
                                            } else {
                                                detail += "Error in Sheet: Campaign on row: " + (row + 1) + ", Campaign with Budget: should not be empty!__";
                                            }
                                        } else {
                                            campaignDTO.setUnlimited(true); // ultimated budge-type
                                            campaignDTO.setBudget(0.0); // budget zero
                                        }
                                    } else {
                                        detail += "Error in Sheet: Campaign on row: " + (row + 1) + ", Campaign with Budget Type: should not be empty (Set Budgets & Unlimited Budget)!__";
                                    }
                                } else {
                                    detail += "Error in Sheet: Campaign on row: " + (row + 1) + ", Campaign with Budget Type: should not be empty (Set Budgets & Unlimited Budget)!__";
                                }
                                // billing period
                                if(campaignsSheetDto.getCampaignBillingPeriod() != null && !campaignsSheetDto.getCampaignBillingPeriod().equals("")) {
                                    Boolean isStartDateOnly = false;
                                    if(campaignsSheetDto.getCampaignBillingPeriod().equals(BILLING_PERIOD_LIST[0]) || campaignsSheetDto.getCampaignBillingPeriod().equals(BILLING_PERIOD_LIST[1])) {
                                        if(campaignsSheetDto.getCampaignBillingPeriod().equals(BILLING_PERIOD_LIST[0])) { isStartDateOnly = true; }
                                    } else {
                                        detail += "Error in Sheet: Campaign on row: " + (row + 1) + ", Campaign with Billing Period: should not be empty (Run my ad set continously starting today,Set a start and end date)!__";
                                    }
                                    // campaign date
                                    if(campaignsSheetDto.getCampaignStartDate() != null && !campaignsSheetDto.getCampaignStartDate().equals("")) {
                                        if(isValidDate(campaignsSheetDto.getCampaignStartDate())) {
                                            Date parsedDate = this.incrementDaysOne(this.dateFormat.parse(campaignsSheetDto.getCampaignStartDate()));
                                            Timestamp timestamp = new java.sql.Timestamp(parsedDate.getTime());
                                            campaignDTO.setStartDate(timestamp);
                                            campaignDTO.setStartTime("00:00:00");
                                        } else {
                                            detail += "Error in Sheet: Campaign on row: " + (row + 1) + ", Campaign with Start Date: not valid should be (yyyy-MM-dd)pattern!__";
                                        }
                                    } else {
                                        detail += "Error in Sheet: Campaign on row: " + (row + 1) + ", Campaign with Start Date: should not be empty!__";
                                    }
                                    // when start date and time there
                                    if(isStartDateOnly == false) {
                                        if(campaignsSheetDto.getCampaignEndDate() != null && !campaignsSheetDto.getCampaignEndDate().equals("")) {
                                            if(isValidDate(campaignsSheetDto.getCampaignEndDate())) {
                                                Date parsedDate = this.incrementDaysOne(this.dateFormat.parse(campaignsSheetDto.getCampaignEndDate()));
                                                Timestamp timestamp = new java.sql.Timestamp(parsedDate.getTime());
                                                campaignDTO.setEndDate(timestamp);
                                                campaignDTO.setEndTime("23:59:59");
                                            } else {
                                                detail += "Error in Sheet: Campaign on row: " + (row + 1) + ", Campaign with End Date: not valid should be (yyyy-MM-dd) pattern!__";
                                            }
                                        } else {
                                            detail += "Error in Sheet: Campaign on row: " + (row + 1) + ", Campaign with End Date: should not be empty!__";
                                        }
                                        if(campaignDTO.getStartDate() != null && campaignDTO.getEndDate() != null) {
                                            if(campaignDTO.getEndDate().before(campaignDTO.getStartDate())) {
                                                detail += "Error in Sheet: Campaign on row: " + (row + 1) + ", Campaign with End Date: should be greater then start date!__";
                                            }
                                        }
                                    }

                                } else {
                                    detail += "Error in Sheet: Campaign on row: " + (row + 1) + ", Campaign with Billing Period: should not be empty (Run my ad set continously starting today,Set a start and end date)!__";
                                }
                            }
                        }
                        // line item
                        if(campaignsSheetDto.getCampaignLineItems() != null && !campaignsSheetDto.getCampaignLineItems().equals("")) {
                            this.lineItemsDetailDto = this.microServicesDetail.getAudienceLineItemsFindByIdAndStatusNot(this.bulkRequest.getToken(), this.bulkRequest.getEntity().get(ID_KEY).toString(), campaignsSheetDto.getCampaignLineItems());
                            if(this.lineItemsDetailDto != null) {
                                if(this.lineItemsDetailDto.getNotFound() != null && this.lineItemsDetailDto.getNotFound().size() > 0) {
                                    detail += "Error in Sheet: Campaign on row: " + (row + 1) + ", Campaign with Line Items : "+ this.lineItemsDetailDto.getNotFound() +" not found!__";
                                }else {
                                    campaignDTO.setLineItems(this.lineItemsDetailDto.getFound().stream().map(line -> { return Long.valueOf(line); }).collect(Collectors.toList()));
                                }
                            }
                        }

                        if (this.isAgAdv.equals("1")) {
                            campaignDTO.setAgencyAdvertiserId(Long.valueOf(this.bulkRequest.getEntity().get(ID_KEY).toString()));
                            campaignDTO.setIsAgAdv(1);
                        } else {
                            campaignDTO.setIsAgAdv(0);
                            campaignDTO.setAgencyAdvertiserId(null);
                        }
                        // it's mean this row oky
                        if(detail.equals("")) {
                            campaignDTO.setBudgetType("revenue");
                            campaignDTO.setBudgetOption(true);
                            this.campaignDTOList.add(campaignDTO);
                        }
                    }
                }
            }
        } else {
            this.isValidationFailed = true;
            this.responseDTO = new ResponseDTO(false, ApiConstants.ERROR_MSG + ": Sheet Object Null", ApiCode.HTTP_400);
        }
        // final check if some
        if(!detail.equals("")) {
            this.isValidationFailed = true;
            message = "Plz Verify Campaign Some Data Wrong";
            this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, detail));
            return;
        }
    }

    private void readCampaignsSheet() throws Exception {
        Integer row = 3;
        String message = "Saving Process Start For Campaigns";
        this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, ""));
        for(CampaignDTO campaignDTO: this.campaignDTOList) {
            ResponseDTO responseDTO = this.microServicesDetail.saveCampaign(this.bulkRequest.getToken(), campaignDTO);
            message = "In Campaigns sheet result for row: " + (row) + " is: " + responseDTO.getMessage();
            this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, ""));
            row = row+1;
        }
    }

    private void validateLineItemSheet() throws Exception {
        String detail = "";
        String message = "";
        this.lineItemDTOLdist = new ArrayList<>();
        if(this.sheet != null) {
            if (this.sheet.getLastRowNum() < 1) { // check the total row in the sheet if result zero it's mean sheet empty
                this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage("Line sheet is empty", ""));
                return;
            } else {
                Integer row = -1;
                Iterator<Row> iterator = this.sheet.iterator();
                Row headingRow = null;
                while (iterator.hasNext()) {
                    row = row + 1; // row start from zero
                    Row currentRow = iterator.next();
                    if (row == 1) {
                        headingRow = currentRow;
                        if (currentRow.getPhysicalNumberOfCells() != 19) {
                            this.isValidationFailed = true;
                            message = "Error in Sheet: Line-Item on row: " + (row+1) + ", Some headings missing";
                        } else {
                            if(!currentRow.getCell(0).getStringCellValue().equals(LINE_ITEM_ID)) {
                                this.isValidationFailed = true;
                                message = "Line-Item :- Heading are not present on proper place";
                                detail += "Error in Sheet: Line-Item on row: " + (row+1) + ", and cell should be " + LINE_ITEM_ID+"__";
                            }
                            if(!currentRow.getCell(1).getStringCellValue().equals(NAME)) {
                                this.isValidationFailed = true;
                                message = "Line-Item :- Heading are not present on proper place";
                                detail += "Error in Sheet: Line-Item on row: " + (row+1) + ", and cell should be " + NAME+"__";
                            }
                            if(!currentRow.getCell(2).getStringCellValue().equals(TYPE)) {
                                this.isValidationFailed = true;
                                message = "Line-Item :- Heading are not present on proper place";
                                detail += "Error in Sheet: Line-Item on row: " + (row+1) + ", and cell should be " + TYPE+"__";
                            }
                            if(!currentRow.getCell(3).getStringCellValue().equals(GROUP)) {
                                this.isValidationFailed = true;
                                message = "Line-Item :- Heading are not present on proper place";
                                detail += "Error in Sheet: Line-Item on row: " + (row+1) + ", and cell should be " + GROUP+"__";
                            }
                            if(!currentRow.getCell(4).getStringCellValue().equals(CAMPAIGN)) {
                                this.isValidationFailed = true;
                                message = "Line-Item :- Heading are not present on proper place";
                                detail += "Error in Sheet: Line-Item on row: " + (row+1) + ", and cell should be " + CAMPAIGN+"__";
                            }
                            if(!currentRow.getCell(5).getStringCellValue().equals(SEGMENT)) {
                                this.isValidationFailed = true;
                                message = "Line-Item :- Heading are not present on proper place";
                                detail += "Error in Sheet: Line-Item on row: " + (row+1) + ", and cell should be " + SEGMENT+"__";
                            }
                            if(!currentRow.getCell(6).getStringCellValue().equals(REVENUE_TYPE)) {
                                this.isValidationFailed = true;
                                message = "Line-Item :- Heading are not present on proper place";
                                detail += "Error in Sheet: Line-Item on row: " + (row+1) + ", and cell should be " + REVENUE_TYPE+"__";
                            }
                            if(!currentRow.getCell(7).getStringCellValue().equals(REVENUE_VALUE)) {
                                this.isValidationFailed = true;
                                message = "Line-Item :- Heading are not present on proper place";
                                detail += "Error in Sheet: Line-Item on row: " + (row+1) + ", and cell should be " + REVENUE_VALUE+"__";
                            }
                            if(!currentRow.getCell(8).getStringCellValue().equals(BUDGET_TYPE)) {
                                this.isValidationFailed = true;
                                message = "Line-Item :- Heading are not present on proper place";
                                detail += "Error in Sheet: Line-Item on row: " + (row+1) + ", and cell should be " + BUDGET_TYPE+"__";
                            }
                            if(!currentRow.getCell(9).getStringCellValue().equals(DAILY_BUDGET)) {
                                this.isValidationFailed = true;
                                message = "Line-Item :- Heading are not present on proper place";
                                detail += "Error in Sheet: Line-Item on row: " + (row+1) + ", and cell should be " + DAILY_BUDGET+"__";
                            }
                            if(!currentRow.getCell(10).getStringCellValue().equals(MIN_BUDGET)) {
                                this.isValidationFailed = true;
                                message = "Line-Item :- Heading are not present on proper place";
                                detail += "Error in Sheet: Line-Item on row: " + (row+1) + ", and cell should be " + MIN_BUDGET+"__";
                            }
                            if(!currentRow.getCell(11).getStringCellValue().equals(MAX_BUDGET)) {
                                this.isValidationFailed = true;
                                message = "Line-Item :- Heading are not present on proper place";
                                detail += "Error in Sheet: Line-Item on row: " + (row+1) + ", and cell should be " + MAX_BUDGET+"__";
                            }
                            if(!currentRow.getCell(12).getStringCellValue().equals(START_DATE)) {
                                this.isValidationFailed = true;
                                message = "Line-Item :- Heading are not present on proper place";
                                detail += "Error in Sheet: Line-Item on row: " + (row+1) + ", and cell should be " + START_DATE+"__";
                            }
                            if(!currentRow.getCell(13).getStringCellValue().equals(END_DATE)) {
                                this.isValidationFailed = true;
                                message = "Line-Item :- Heading are not present on proper place";
                                detail += "Error in Sheet: Line-Item on row: " + (row+1) + ", and cell should be " + END_DATE+"__";
                            }
                            if(!currentRow.getCell(14).getStringCellValue().equals(CREATIVES)) {
                                this.isValidationFailed = true;
                                message = "Line-Item :- Heading are not present on proper place";
                                detail += "Error in Sheet: Line-Item on row: " + (row+1) + ", and cell should be " + CREATIVES+"__";
                            }
                            if(!currentRow.getCell(15).getStringCellValue().equals(EXCLUDED_PUBLISHERS)) {
                                this.isValidationFailed = true;
                                message = "Line-Item :- Heading are not present on proper place";
                                detail += "Error in Sheet: Line-Item on row: " + (row+1) + ", and cell should be " + EXCLUDED_PUBLISHERS+"__";
                            }
                            if(!currentRow.getCell(16).getStringCellValue().equals(OPTIMIZATION_METHOD)) {
                                this.isValidationFailed = true;
                                message = "Line-Item :- Heading are not present on proper place";
                                detail += "Error in Sheet: Line-Item on row: " + (row+1) + ", and cell should be " + OPTIMIZATION_METHOD+"__";
                            }
                            if(!currentRow.getCell(17).getStringCellValue().equals(OPTIMIZATION_AMOUNT)) {
                                this.isValidationFailed = true;
                                message = "Line-Item :- Heading are not present on proper place";
                                detail += "Error in Sheet: Line-Item on row: " + (row+1) + ", and cell should be " + OPTIMIZATION_AMOUNT+"__";
                            }
                            if(!currentRow.getCell(18).getStringCellValue().equals(GOAL_PRIORITY)) {
                                this.isValidationFailed = true;
                                message = "Line-Item :- Heading are not present on proper place";
                                detail += "Error in Sheet: Line-Item on row: " + (row+1) + ", and cell should be " + GOAL_PRIORITY+"__";
                            }
//                            if(!currentRow.getCell(19).getStringCellValue().equals(COUNTRY_TARGET)) {
//                                this.isValidationFailed = true;
//                                message = "Line-Item :- Heading are not present on proper place";
//                                detail += "Error in Sheet: Line-Item on row: " + (row+1) + ", and cell should be " + COUNTRY_TARGET+"__";
//                            }
                        }
                        if(this.isValidationFailed) {
                            this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, detail));
                            return;
                        }
                    } else if (row > 1) {
                        LineItemSheetDto lineItemSheetDto = new LineItemSheetDto();
                        Cell currentCell = currentRow.getCell(0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(0).getStringCellValue().equals(LINE_ITEM_ID) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            lineItemSheetDto.setLineItemId(currentCell.getStringCellValue().replace(".0",""));
                        }
                        currentCell = currentRow.getCell(1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(1).getStringCellValue().equals(NAME) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            lineItemSheetDto.setLineItemName(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(2, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(2).getStringCellValue().equals(TYPE) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            lineItemSheetDto.setLineItemType(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(3, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(3).getStringCellValue().equals(GROUP) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            lineItemSheetDto.setLineItemGroup(currentCell.getStringCellValue().replace(".0",""));
                        }
                        currentCell = currentRow.getCell(4, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(4).getStringCellValue().equals(CAMPAIGN) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            lineItemSheetDto.setLineItemCampaign(currentCell.getStringCellValue().replace(".0",""));
                        }
                        currentCell = currentRow.getCell(5, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(5).getStringCellValue().equals(SEGMENT) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            lineItemSheetDto.setLineItemSegment(currentCell.getStringCellValue().replace(".0",""));
                        }
                        currentCell = currentRow.getCell(6, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(6).getStringCellValue().equals(REVENUE_TYPE) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            lineItemSheetDto.setLineItemRevenueType(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(7, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(7).getStringCellValue().equals(REVENUE_VALUE) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            lineItemSheetDto.setLineItemRevenueValue(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(8, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(8).getStringCellValue().equals(BUDGET_TYPE) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            lineItemSheetDto.setLineItemBudgetType(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(9, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(9).getStringCellValue().equals(DAILY_BUDGET) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            lineItemSheetDto.setLineItemDailyBudget(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(10, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(10).getStringCellValue().equals(MIN_BUDGET) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            lineItemSheetDto.setLineItemMinBudget(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(11, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(11).getStringCellValue().equals(MAX_BUDGET) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            lineItemSheetDto.setLineItemMaxBudget(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(12, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(12).getStringCellValue().equals(START_DATE) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            lineItemSheetDto.setLineItemStartDate(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(13, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(13).getStringCellValue().equals(END_DATE) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            lineItemSheetDto.setLineItemEndDate(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(14, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(14).getStringCellValue().equals(CREATIVES) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            lineItemSheetDto.setLineItemCreatives(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(15, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(15).getStringCellValue().equals(EXCLUDED_PUBLISHERS) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            lineItemSheetDto.setLineItemExcludePublihser(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(16, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(16).getStringCellValue().equals(OPTIMIZATION_METHOD) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            lineItemSheetDto.setLineItemOptimizationMethod(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(17, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(17).getStringCellValue().equals(OPTIMIZATION_AMOUNT) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            lineItemSheetDto.setLineItemOptimizationAmount(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(18, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(18).getStringCellValue().equals(GOAL_PRIORITY) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            lineItemSheetDto.setLineItemGoalPriority(currentCell.getStringCellValue());
                        }
//                        currentCell = currentRow.getCell(19, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
//                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
//                        if(headingRow.getCell(19).getStringCellValue().equals(COUNTRY_TARGET) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
//                            lineItemSheetDto.setLineItemCountryTarget(currentCell.getStringCellValue());
//                        }

                        //====================================Row-Data-Validate=======================================
                        LineItemDTO lineItemDTO = new LineItemDTO();
                        // line-item id
                        if(lineItemSheetDto.getLineItemId() != null && !lineItemSheetDto.getLineItemId().equals("")) {
                            if(this.microServicesDetail.campLanFindByIdAndStatusNot(this.bulkRequest.getToken(),lineItemSheetDto.getLineItemId(), Status.Delete) != null) {
                                boolean isNumeric = lineItemSheetDto.getLineItemId().chars().allMatch( Character::isDigit );
                                if(isNumeric) {
                                    lineItemDTO.setId(Long.valueOf(lineItemSheetDto.getLineItemId()));
                                } else {
                                    detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with id: should not be number empty!__";
                                }
                            } else {
                                detail += "Error in Sheet: Line-Item Other on row: " + (row + 1) + ", Line-Item with id: " + lineItemSheetDto.getLineItemId() + " don't exists!__";
                            }
                        }
                        // line-item name
                        if(lineItemSheetDto.getLineItemName() != null && !lineItemSheetDto.getLineItemName().equals("")) {
                            lineItemDTO.setName(lineItemSheetDto.getLineItemName());
                        } else {
                            detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with name: should not be empty!__";
                        }
                        // line-item type
                        if(lineItemSheetDto.getLineItemType() != null && !lineItemSheetDto.getLineItemType().equals("")) {
                            if(lineItemSheetDto.getLineItemType().equals(CAMPAIGNS_TYPE[1])) {
                                lineItemDTO.setType(ApiConstants.FACEBOOK);
                            } else {
                                lineItemDTO.setType(ApiConstants.WEB);
                            }
                        } else {
                            detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with type: should not be empty!__";
                        }
                        // group
                        if(lineItemSheetDto.getLineItemGroup() != null && !lineItemSheetDto.getLineItemGroup().equals("")) {
                            AdvertiserGroup advertiserGroup = this.microServicesDetail.getGroupFindByIdAndStatusNot(this.bulkRequest.getToken(), lineItemSheetDto.getLineItemGroup(), Status.Delete);
                            if(advertiserGroup != null) {
                                lineItemDTO.setAdvertiserGroupId(advertiserGroup.getId());
                            } else {
                                detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with Group: "+lineItemSheetDto.getLineItemGroup()+" don't exists! __!__";
                            }
                        } else {
                            detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with Group: should not be empty!__";
                        }
                        // campaign
                        if(lineItemSheetDto.getLineItemCampaign() != null && !lineItemSheetDto.getLineItemCampaign().equals("")) {
                            boolean isNumeric = lineItemSheetDto.getLineItemCampaign().chars().allMatch( Character::isDigit );
                            if(isNumeric) {
                                Campaign campaign = this.microServicesDetail.getCampFindByIdAndStatusNot(this.bulkRequest.getToken(), lineItemSheetDto.getLineItemCampaign(), Status.Delete);
                                if(campaign != null) {
                                    lineItemDTO.setCampaignId(campaign.getId());
                                    lineItemDTO.setInsertionOrderId(campaign.getId());
                                } else {
                                    detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with Campaign: "+lineItemSheetDto.getLineItemCampaign()+" don't exists! __!__";
                                }
                            } else {
                                detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with Campaign: should be number!__";
                            }
                        }
                        // Segment
                        if(lineItemSheetDto.getLineItemSegment() != null && !lineItemSheetDto.getLineItemSegment().equals("")) {
                            boolean isNumeric = lineItemSheetDto.getLineItemSegment().chars().allMatch( Character::isDigit );
                            if(isNumeric) {
                                SegmentDtoBulk segmentDtoBulk = this.microServicesDetail.getSegmentsFindByIdAndStatusNot(this.bulkRequest.getToken(), lineItemSheetDto.getLineItemSegment(), Status.Delete);
                                if(segmentDtoBulk != null) { ;
                                    List list = new ArrayList<Long>();
                                    list.add(Long.valueOf(lineItemSheetDto.getLineItemSegment()));
                                    lineItemDTO.setSegment(list);
                                } else {
                                    detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with Segment: "+lineItemSheetDto.getLineItemSegment()+" don't exists!__";
                                }
                            } else {
                                detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with Segment: should be number!__";
                            }
                        }
                        // face book
                        if((lineItemSheetDto.getLineItemType() != null && !lineItemSheetDto.getLineItemType().equals("")) && lineItemSheetDto.getLineItemType().equals(CAMPAIGNS_TYPE[1])) {
                            // min-budget
                            if(lineItemSheetDto.getLineItemMinBudget() != null && !lineItemSheetDto.getLineItemMinBudget().equals("")) {
                                if(isNumeric(lineItemSheetDto.getLineItemMinBudget().trim())) {
                                    lineItemDTO.setMinMargin(Double.valueOf(lineItemSheetDto.getLineItemMinBudget()));
                                } else {
                                    detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with Min-Budget: should be numeric!__";
                                }
                            } else {// commented below by SA 30Jan2020, min and max budget can be empty
//                                detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with Min-Budget: should be number!__";
                            }
                            // max-budget
                            if(lineItemSheetDto.getLineItemMaxBudget() !=  null && !lineItemSheetDto.getLineItemMaxBudget().equals("")) {
                                if(isNumeric(lineItemSheetDto.getLineItemMaxBudget().trim())) {
                                    lineItemDTO.setMaxMargin(Double.valueOf(lineItemSheetDto.getLineItemMaxBudget()));
                                } else {
                                    detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with Max-Budget: should be numeric!__";
                                }
                            } else {// commented below by SA
//                                detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with Max-Budget: should be number!__";
                            }
                        } else if((lineItemSheetDto.getLineItemType() != null && !lineItemSheetDto.getLineItemType().equals("")) && lineItemSheetDto.getLineItemType().equals(CAMPAIGNS_TYPE[0])) { // web
                            // revenue type
                            if(lineItemSheetDto.getLineItemRevenueType() != null && !lineItemSheetDto.getLineItemRevenueType().equals("")) {
                                lineItemDTO.setRevenueType(lineItemSheetDto.getLineItemRevenueType().toLowerCase());
                            } else {
                                detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with Revenue Type: should be (Cost Plus) !__";
                            }
                            // revenu budget
                            if(lineItemSheetDto.getLineItemRevenueValue() != null && !lineItemSheetDto.getLineItemRevenueValue().equals("")) {
                                if(isNumeric(lineItemSheetDto.getLineItemRevenueValue())) {
                                    lineItemDTO.setRevenueValue(Double.valueOf(lineItemSheetDto.getLineItemRevenueValue()));
                                } else {
                                    detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with Revenue: should be numeric!__";
                                }
                            } else {
                                detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with Revenue Value: should not be empty!__";
                            }
                             //budget type
                            if(lineItemSheetDto.getLineItemBudgetType() != null && !lineItemSheetDto.getLineItemBudgetType().equals("")) {
                                if(lineItemSheetDto.getLineItemBudgetType().equals(LINE_ITEM_BUDGETS_TYPE[0])) {
                                    lineItemDTO.setBudgetType("revenue");
                                } else {
                                    detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with Revenue: should be (Set Budgets)!__";
                                }
                            } else {
                                detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with Budget Type: should not be empty!__";
                            }
                            // daily budget
                            if(lineItemSheetDto.getLineItemDailyBudget() != null && !lineItemSheetDto.getLineItemDailyBudget().equals("")) {
                                if(isNumeric(lineItemSheetDto.getLineItemDailyBudget())) {
                                    lineItemDTO.setDailyBudget(Double.valueOf(lineItemSheetDto.getLineItemDailyBudget()));
                                } else {
                                    detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with Revenue: should be numeric!__";
                                }
                            } else {
                                detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with Daily Budget: should not be empty!__";
                            }
                            // min-budget
                            if(lineItemSheetDto.getLineItemMinBudget() != null && !lineItemSheetDto.getLineItemMinBudget().equals("")) {
                                if(isNumeric(lineItemSheetDto.getLineItemMinBudget())) {
                                    lineItemDTO.setMinMargin(Double.valueOf(lineItemSheetDto.getLineItemMinBudget()));
                                } else {
                                    detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with Min-Budget: should be numeric!__";
                                }
                            } else {// commented below by SA 30Jan2020, min and max budget can be empty
//                                detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with Min-Budget: should be number!__";
                            }
                            // max-budget
                            if(lineItemSheetDto.getLineItemMaxBudget() !=  null && !lineItemSheetDto.getLineItemMaxBudget().equals("")) {
                                if(isNumeric(lineItemSheetDto.getLineItemMaxBudget())) {
                                    lineItemDTO.setMaxMargin(Double.valueOf(lineItemSheetDto.getLineItemMaxBudget()));
                                } else {
                                    detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with Max-Budget: should be numeric!__";
                                }
                            } else {// commented below by SA 30Jan2020, min and max budget can be empty
//                                detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with Max-Budget: should be number!__";
                            }
                            // exclude-publihser
                            if(lineItemSheetDto.getLineItemExcludePublihser() != null && !lineItemSheetDto.getLineItemExcludePublihser().equals("")) {
                                this.sellerMemberIdDetailDto = this.microServicesDetail.getSellersFindBySellerMemberIdBulk(this.bulkRequest.getToken(), this.bulkRequest.getEntity().get(ID_KEY).toString());
                                if(this.sellerMemberIdDetailDto == null  && this.sellerMemberIdDetailDto.getNotFound() != null && this.sellerMemberIdDetailDto.getNotFound().size() > 0) {
                                    detail += "Error in Sheet:  Line-Item on row: " + (row + 1) + ",  Line-Item with Seller: "+ this.sellerMemberIdDetailDto.getNotFound() +" not found!__";
                                }else {
                                    lineItemDTO.setExcludedSellers(this.sellerMemberIdDetailDto.getFound().stream().map(sellerID -> { return Integer.valueOf(sellerID); }).collect(Collectors.toList()));
                                }
                            }
                            // optimization method
                            if(lineItemSheetDto.getLineItemOptimizationMethod() != null && !lineItemSheetDto.getLineItemOptimizationMethod().equals("")) {
                                if(lineItemSheetDto.getLineItemOptimizationMethod().equals(OPTIMIZATION_METHOD_LIST[0]) || lineItemSheetDto.getLineItemOptimizationMethod().equals(OPTIMIZATION_METHOD_LIST[1])) {
                                    if(lineItemSheetDto.getLineItemOptimizationMethod().equals(OPTIMIZATION_METHOD_LIST[0])) {
                                        lineItemDTO.setOptimizationMethod("cpa");
                                    } else {
                                        lineItemDTO.setOptimizationMethod("none");
                                    }
                                } else {
                                    detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with Optimization method: should be (Enable, Disable)!__";
                                }
                            } else {
                                detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with Optimization method: should be (Enable, Disable)!__";
                            }
                            // line-optim
                            if((!lineItemSheetDto.getLineItemOptimizationMethod().equals("") && lineItemSheetDto.getLineItemOptimizationMethod().equals(OPTIMIZATION_METHOD_LIST[0])) && (lineItemSheetDto.getLineItemOptimizationAmount() != null && !lineItemSheetDto.getLineItemOptimizationAmount().equals(""))) {
                                if(isNumeric(lineItemSheetDto.getLineItemOptimizationAmount())) {
                                    lineItemDTO.setOptimizationAmount(Double.valueOf(lineItemSheetDto.getLineItemOptimizationAmount()));
                                } else {
                                    detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with Optimization Amount: should be numeric!__";
                                }
                            }
                            // priority
                            if(lineItemSheetDto.getLineItemGoalPriority() != null && !lineItemSheetDto.getLineItemGoalPriority().equals("")) {
                                if(lineItemSheetDto.getLineItemGoalPriority().equals(GOAL_PRIORITY_LIST[0]) || lineItemSheetDto.getLineItemGoalPriority().equals(GOAL_PRIORITY_LIST[1])) {
                                    if(lineItemSheetDto.getLineItemGoalPriority().equals(GOAL_PRIORITY_LIST[0])) {
                                        lineItemDTO.setGoalPiriority(true);
                                    } else {
                                        lineItemDTO.setGoalPiriority(false);
                                    }
                                } else {
                                    detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with Priority : should be (Delivery,Performance)!__";
                                }
                            } else {
                                detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with Priority : should be (Delivery,Performance)!__";
                            }

                        }
                        // line start date
                        if(lineItemSheetDto.getLineItemStartDate() != null && !lineItemSheetDto.getLineItemStartDate().equals("")) {
                            if(isValidDate(lineItemSheetDto.getLineItemStartDate())) {
                                Date parsedDate = this.incrementDaysOne(this.lineItemDateFormat.parse(this.lineItemDateFormat.format(this.dateFormat.parse(lineItemSheetDto.getLineItemStartDate()))));
                                Timestamp timestamp = new java.sql.Timestamp(parsedDate.getTime());
                                lineItemDTO.setStartDate(timestamp);
                                lineItemDTO.setStartTime("00:00:00");
                            } else {
                                detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Campaign with Start Date: not valid should be (yyyy-MM-dd) pattern!__";
                            }
                        } else {
                            detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Campaign with Start Date: not valid should be (yyyy-MM-dd) pattern!__";
                        }
                        // line-item end date
                        if(lineItemSheetDto.getLineItemEndDate() != null && !lineItemSheetDto.getLineItemEndDate().equals("")) {
                            if(isValidDate(lineItemSheetDto.getLineItemEndDate())) {
                                Date parsedDate = this.incrementDaysOne(this.lineItemDateFormat.parse(this.lineItemDateFormat.format(this.dateFormat.parse(lineItemSheetDto.getLineItemEndDate()))));
                                Timestamp timestamp = new java.sql.Timestamp(parsedDate.getTime());
                                lineItemDTO.setEndDate(timestamp);
                                lineItemDTO.setEndTime("23:59:59");
                                if(lineItemDTO.getEndDate().before(lineItemDTO.getStartDate())) {
                                    detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Campaign with End Date: not valid should be it's less then start date!__";
                                }
                            } else {
                                detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Campaign with End Date: not valid should be (yyyy-MM-dd)pattern!__";
                            }
                        } else {
                            detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Campaign with SEnd Date: not valid should be (yyyy-MM-dd) pattern!__";
                        }
                        // creatives
                        if(lineItemSheetDto.getLineItemCreatives() != null && !lineItemSheetDto.getLineItemCreatives().equals("")) {
                            this.creativeDetailDto = this.microServicesDetail.getAudienceCreativeDetailFindByIdAndStatusNot(this.bulkRequest.getToken(), this.bulkRequest.getEntity().get(ID_KEY).toString(), lineItemSheetDto.getLineItemCreatives());
                            if(this.creativeDetailDto == null  && this.creativeDetailDto.getNotFound() != null && this.creativeDetailDto.getNotFound().size() > 0) {
                                detail += "Error in Sheet:  Line-Item on row: " + (row + 1) + ",  Line-Item with Creative: "+ this.creativeDetailDto.getNotFound() +" not found!__";
                            }else {
                                lineItemDTO.setCreative(this.creativeDetailDto.getFound().stream().map(sellerID -> { return Long.valueOf(sellerID); }).collect(Collectors.toList()));
                            }
                        }
                        // country-target
//                        if(lineItemSheetDto.getLineItemCountryTarget() != null && !lineItemSheetDto.getLineItemCountryTarget().equals("")) {
//                            if(lineItemSheetDto.getLineItemCountryTarget().equalsIgnoreCase("USA")) {
//                                QuorumCountryDTO quorumCountryDTO = new QuorumCountryDTO();
//                                quorumCountryDTO.setChecked("true");
//                                List<QuorumCountryDTO> country = new ArrayList<>();
//                                country.add(quorumCountryDTO);
//                                lineItemDTO.setCountry(country);
//                            } else {
//                                detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with Country-Target : should be (USA)!__";
//                            }
//                        } else {
//                            detail += "Error in Sheet: Line-Item on row: " + (row + 1) + ", Line-Item with Country-Target : should be (USA)!__";
//                        }

                        if (this.isAgAdv.equals("1")) {
                            lineItemDTO.setAgencyAdvertiserId(Long.valueOf(this.bulkRequest.getEntity().get(ID_KEY).toString()));
                        } else {
                            lineItemDTO.setAgencyAdvertiserId(null);
                        }
                        // it's mean this row oky
                        if(detail.equals("")) { this.lineItemDTOLdist.add(lineItemDTO); }
                    }
                }
            }
        } else {
            this.isValidationFailed = true;
            this.responseDTO = new ResponseDTO(false, ApiConstants.ERROR_MSG + ": Sheet Object Null", ApiCode.HTTP_400);
        }
        // final check if some
        if(!detail.equals("")) {
            this.isValidationFailed = true;
            message = "Plz Verify Line Some Data Wrong";
            this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, detail));
            return;
        }
    }

    private void readLineItemSheet() throws Exception {
        Integer row = 3;
        String message = "Saving Process Start For LineItems";
        this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, ""));
        for(LineItemDTO lineItemDTO: this.lineItemDTOLdist) {
            ResponseDTO responseDTO = this.microServicesDetail.saveLineItems(this.bulkRequest.getToken(), lineItemDTO);
            message = "In LineItems sheet result for row: " + (row) + " is: " + responseDTO.getMessage();
            this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, ""));
            row = row+1;
        }
    }

    private void validateManageCreativesSheet() throws Exception {
        String detail = "";
        String message = "";
        this.creativesDTOList = new ArrayList<>();
        if(this.sheet != null) {
            if (this.sheet.getLastRowNum() < 1) { // check the total row in the sheet if result zero it's mean sheet empty
                this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage("Manage Creative sheet is empty", ""));
                return;
            } else { // sheet have data so validation process start
                Integer row = -1;
                Iterator<Row> iterator = this.sheet.iterator();
                Row headingRow = null;
                while (iterator.hasNext()) {
                    row = row + 1; // row start from zero
                    Row currentRow = iterator.next();
                    if (row == 1) {
                        headingRow = currentRow;
                        if (currentRow.getPhysicalNumberOfCells() != 7) {
                            this.isValidationFailed = true;
                            message = "Error in Sheet: Manage Creative on row: " + (row+1) + ", Some headings missing";
                        } else {
                            if(!currentRow.getCell(0).getStringCellValue().equals(CREATIVE_ID)) {
                                this.isValidationFailed = true;
                                message = "Manage Creative :- Heading are not present on proper place";
                                detail += "Error in Sheet: Manage Creative on row: " + (row+1) + ", and cell should be " + CREATIVE_ID+"__";
                            }
                            if(!currentRow.getCell(1).getStringCellValue().equals(NAME)) {
                                this.isValidationFailed = true;
                                message = "Manage Creative :- Heading are not present on proper place";
                                detail += "Error in Sheet: Manage Creative on row: " + (row+1) + ", and cell should be " + NAME+"__";
                            }
                            if(!currentRow.getCell(2).getStringCellValue().equals(DIMENTIONS)) {
                                this.isValidationFailed = true;
                                message = "Manage Creative :- Heading are not present on proper place";
                                detail += "Error in Sheet: Manage Creative on row: " + (row+1) + ", and cell should be " + DIMENTIONS+"__";
                            }
                            if(!currentRow.getCell(3).getStringCellValue().equals(AUDIT_TYPE)) {
                                this.isValidationFailed = true;
                                message = "Manage Creative :- Heading are not present on proper place";
                                detail += "Error in Sheet: Manage Creative on row: " + (row+1) + ", and cell should be " + AUDIT_TYPE+"__";
                            }
                            if(!currentRow.getCell(4).getStringCellValue().equals(CLICK_URL)) {
                                this.isValidationFailed = true;
                                message = "Manage Creative :- Heading are not present on proper place";
                                detail += "Error in Sheet: Manage Creative on row: " + (row+1) + ", and cell should be " + CLICK_URL+"__";
                            }
                            if(!currentRow.getCell(5).getStringCellValue().equals(IMG_URL)) {
                                this.isValidationFailed = true;
                                message = "Manage Creative :- Heading are not present on proper place";
                                detail += "Error in Sheet: Manage Creative on row: " + (row+1) + ", and cell should be " + IMG_URL+"__";
                            }
                            if(!currentRow.getCell(6).getStringCellValue().equals(LINE_ITEMS)) {
                                this.isValidationFailed = true;
                                message = "Manage Creative :- Heading are not present on proper place";
                                detail += "Error in Sheet: Manage Creative on row: " + (row+1) + ", and cell should be " + LINE_ITEMS+"__";
                            }
                        }
                        if(this.isValidationFailed) {
                            this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, detail));
                            return;
                        }
                    } else if (row > 1) {
                        CreativeSheetDto creativeSheetDto = new CreativeSheetDto();
                        Cell currentCell = currentRow.getCell(0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(0).getStringCellValue().equals(CREATIVE_ID) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            creativeSheetDto.setCreativeId(currentCell.getStringCellValue().replace(".0",""));
                        }
                        currentCell = currentRow.getCell(1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(1).getStringCellValue().equals(NAME) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            creativeSheetDto.setCreativeName(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(2, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(2).getStringCellValue().equals(DIMENTIONS) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            creativeSheetDto.setCreativeDimentions(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(3, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(3).getStringCellValue().equals(AUDIT_TYPE) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            creativeSheetDto.setCreativeAuditType(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(4, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(4).getStringCellValue().equals(CLICK_URL) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            creativeSheetDto.setCreativeClickUrl(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(5, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(5).getStringCellValue().equals(IMG_URL) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            creativeSheetDto.setCreativeImgUrl(currentCell.getStringCellValue());
                        }
                        currentCell = currentRow.getCell(6, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        currentCell.setCellType(Cell.CELL_TYPE_STRING);
                        if(headingRow.getCell(6).getStringCellValue().equals(LINE_ITEMS) && currentCell.getCellTypeEnum() != CellType.BLANK && !currentCell.getStringCellValue().equals("")) {
                            creativeSheetDto.setCreativeLineItems(currentCell.getStringCellValue());
                        }

                        CreativesDTO creativesDTO = new CreativesDTO();
                        // creative id
                        if(creativeSheetDto.getCreativeId() != null && !creativeSheetDto.getCreativeId().equals("")) {
                            if(this.microServicesDetail.creativeFindByIdAndStatusNot(this.bulkRequest.getToken(),creativeSheetDto.getCreativeId(), this.bulkRequest.getEntity().get(ID_KEY).toString(),Status.Delete) != null) {
                                boolean isNumeric = creativeSheetDto.getCreativeId().chars().allMatch(Character::isDigit );
                                if(isNumeric) {
                                    creativesDTO.setId(Long.valueOf(creativeSheetDto.getCreativeId()));
                                } else {
                                    detail += "Error in Sheet: Creative on row: " + (row + 1) + ", Creative with id: should be number!__";
                                }
                            } else {
                                detail += "Error in Sheet: Creative on row: " + (row + 1) + ", Creative with id: " + creativeSheetDto.getCreativeId() + " don't exists!__";
                            }
                        }
                        // name
                        if(creativeSheetDto.getCreativeName() != null && !creativeSheetDto.getCreativeName().equals("")) {
                            creativesDTO.setName(creativeSheetDto.getCreativeName());
                        } else {
                            detail += "Error in Sheet: Creative on row: " + (row + 1) + ", Creative with name: should be number!__";
                        }
                        // demention
                        if(creativeSheetDto.getCreativeDimentions() != null && !creativeSheetDto.getCreativeDimentions().equals("")) {
                            if(creativeSheetDto.getCreativeDimentions().equals(SIZE_CREATIVE[0]) || creativeSheetDto.getCreativeDimentions().equals(SIZE_CREATIVE[1]) ||
                                    creativeSheetDto.getCreativeDimentions().equals(SIZE_CREATIVE[2]) /*|| creativeSheetDto.getCreativeDimentions().equals(SIZE_CREATIVE[3]) commented by SA*/  ) {
                                String[] split = creativeSheetDto.getCreativeDimentions().split("x");
                                creativesDTO.setHeight(new Integer(split[1].trim()));
                                creativesDTO.setWidth(new Integer(split[0].trim()));
                            } else {
                                detail += "Error in Sheet: Creative on row: " + (row + 1) + ", Creative with Dimentions: wrong be (300 x 250,320 x 50,300 x 50)!__";
                            }
                        } else {
                            detail += "Error in Sheet: Creative on row: " + (row + 1) + ", Creative with Dimentions: should be number!__";
                        }
                        // audit type
                        if(creativeSheetDto.getCreativeAuditType() != null && !creativeSheetDto.getCreativeAuditType().equals("")) {
                            if(creativeSheetDto.getCreativeAuditType().trim().equals(AUDIT_TYPE_LIST[0]) || creativeSheetDto.getCreativeAuditType().trim().equals(AUDIT_TYPE_LIST[1])) {
                                if(creativeSheetDto.getCreativeAuditType().trim().equals(AUDIT_TYPE_LIST[0])) {
                                    creativesDTO.setIsSelfAudited(false);
                                } else {
                                    creativesDTO.setIsSelfAudited(true);
                                }
                            } else {
                                detail += "Error in Sheet: Creatives on row: " + (row + 1) + ", Creative with Audit Type should (Audit, Self Audit)!__";
                            }
                        } else {
                            detail += "Error in Sheet: Creatives on row: " + (row + 1) + ", Creative with Audit Type should (Audit, Self Audit)!__";
                        }
                        // click url
                        if (creativeSheetDto.getCreativeClickUrl() != null &&  !creativeSheetDto.getCreativeClickUrl().equals("")) {
                            if ((creativeSheetDto.getCreativeClickUrl() != null && creativeSheetDto.getCreativeClickUrl().length() > 0) && isValidURL(creativeSheetDto.getCreativeClickUrl())) {
                                creativesDTO.setClickURL(creativeSheetDto.getCreativeClickUrl());
                            } else {
                                detail += "Error in Sheet: Creatives on row: " + (row + 1) + ", Click URL not valid __";
                            }
                        } else {
                            detail += "Error in Sheet: Creatives on row: " + (row + 1) + ", Click URL should not be empty __";
                        }
                        // image url
                        if (creativeSheetDto.getCreativeImgUrl() != null &&  !creativeSheetDto.getCreativeImgUrl().equals("")) {
                            if ((creativeSheetDto.getCreativeImgUrl() != null && creativeSheetDto.getCreativeImgUrl().length() > 0) && isValidURL(creativeSheetDto.getCreativeImgUrl())) {
                                creativesDTO.setImgURL(creativeSheetDto.getCreativeImgUrl());
                                creativesDTO.setExistingImgURL(creativeSheetDto.getCreativeImgUrl());
                            } else {
                                detail += "Error in Sheet: Creatives on row: " + (row + 1) + ", Image URL not valid __";
                            }
                        } else {
                            detail += "Error in Sheet: Creatives on row: " + (row + 1) + ", Image URL should not be empty __";
                        }
                        // line item
                        if(creativeSheetDto.getCreativeLineItems() != null && !creativeSheetDto.getCreativeLineItems().equals("")) {
                            this.lineItemsDetailDto = this.microServicesDetail.getAudienceLineItemsFindByIdAndStatusNot(this.bulkRequest.getToken(), this.bulkRequest.getEntity().get(ID_KEY).toString(), creativeSheetDto.getCreativeLineItems().trim().replace(" ",""));
                            if(this.lineItemsDetailDto != null) {
                                if(this.lineItemsDetailDto.getNotFound() != null && this.lineItemsDetailDto.getNotFound().size() > 0) {
                                    detail += "Error in Sheet: Creatives on row: " + (row + 1) + ", Creatives with Line Items : "+ this.lineItemsDetailDto.getNotFound() +" not found!__";
                                }else {
                                    creativesDTO.setLineItemIds(this.lineItemsDetailDto.getFound().stream().map(lineItemId -> { return Long.valueOf(lineItemId); }).collect(Collectors.toList()));
                                }
                            }
                        }
                        if (this.isAgAdv.equals("1")) {
                            creativesDTO.setAgencyAdvertiserId(Long.valueOf(this.bulkRequest.getEntity().get(ID_KEY).toString()));
                            creativesDTO.setIsAgAdv(1);
                        } else {
                            creativesDTO.setIsAgAdv(0);
                            creativesDTO.setAgencyAdvertiserId(null);
                        }
                        // it's mean this row oky
                        if(detail.equals("")) { this.creativesDTOList.add(creativesDTO); }
                    }
                }
            }
        } else {
            this.isValidationFailed = true;
            this.responseDTO = new ResponseDTO(false, ApiConstants.ERROR_MSG + ": Sheet Object Null", ApiCode.HTTP_400);
        }
        // final check if some
        if(!detail.equals("")) {
            this.isValidationFailed = true;
            message = "Plz Verify Creative Some Data Wrong";
            this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, detail));
            return;
        }
    }

    private void readManageCreativesSheet() throws Exception {
        Integer row = 3;
        String message = "Saving Process Start For Creatives";
        this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, ""));
        for(CreativesDTO creativesDTO: this.creativesDTOList) {
            ResponseDTO responseDTO = this.microServicesDetail.saveCreative(this.bulkRequest.getToken(), creativesDTO, creativesDTO.getId() != null ? true : false);
            message = "In Creative sheet result for row: " + (row) + " is: " + responseDTO.getMessage();
            this.socketServerComponent.sendSocketEventToClient(this.authUser.getId(), this.socketServerComponent.generateMessage(message, ""));
            row = row+1;
        }
    }

    private SegmentScheduleDTO flightDate(String flight) {
        SegmentScheduleDTO segmentScheduleDTO = null;
        if(flight != null) {
            //[startDate=02-11-2019,startTime=00:00:00,endDate=02-11-2019,endTime=00:00:00]
            if(flight.charAt(0) == '[') {
                if(flight.charAt(flight.length()-1) == ']') {
                    flight = flight.substring(1, flight.length()-1);
                } else {
                    flight = flight.substring(1, flight.length());
                }
            }
            //startDate=02-11-2019,startTime=00:00:00,endDate=02-11-2019,endTime=00:00:00
            String flightDetail[] = flight.split(",");
            if(flightDetail.length == 4) {
                try {
                    String startDate = flightDetail[0].split("=")[1];
                    String startTime = flightDetail[1].split("=")[1];
                    String endDate = flightDetail[2].split("=")[1];
                    String endTime = flightDetail[3].split("=")[1];
                    segmentScheduleDTO = new SegmentScheduleDTO();
                    segmentScheduleDTO.setStartTime(startTime);
                    segmentScheduleDTO.setEndTime(endTime);
                    Date start = this.incrementDaysOne(new SimpleDateFormat("dd/MM/yyyy").parse(startDate.replace("-","/")));
                    Date end = this.incrementDaysOne(new SimpleDateFormat("dd/MM/yyyy").parse(endDate.replace("-","/")));
                    if (end.equals(start) || end.after(start)) {
                        segmentScheduleDTO.setStartDate(start);
                        segmentScheduleDTO.setEndDate(end);
                    } else {
                        segmentScheduleDTO = null;
                    }
                } catch (Exception ex) {
                    segmentScheduleDTO = null;
                }
            }
        }
        return segmentScheduleDTO;
    }

    private Date incrementDaysOne(Date date) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        c.add(Calendar.DATE, 1);
        date = c.getTime();
        return date;
    }

    public static void main(String args[]) throws Exception {
//        QuorumCountryDTO quorumCountryDTO = new QuorumCountryDTO();
//        quorumCountryDTO.setChecked("true");
//        List<QuorumCountryDTO> country = new ArrayList<>();
//        country.add(quorumCountryDTO);
//        System.out.println(quorumCountryDTO);
//        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd/MM/yyyy");
//        String formate = "2019-10-22";
//        System.out.println("Start-Date :- " + simpleDateFormat.parse(formate.replace("-","/")));
//        Date start =simpleDateFormat.parse(formate);


        String ds1 = "2007-06-30";
        SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdf2 = new SimpleDateFormat("dd/MM/yyyy");
        String ds2 = sdf2.format(sdf1.parse(ds1));
        System.out.println(ds2);
    }

}
