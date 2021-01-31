package org.quorum.service.imp;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.quorum.domain.dto.BulkRequest;
import org.quorum.entity.dto.SellersDTO;
import org.quorum.service.IWriteDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;


@Service
@Scope("prototype")
public class PublishersSellersWriteService extends PoiWrokBookUtil implements IWriteDataService {

    public Logger logger = LogManager.getLogger(PublishersSellersWriteService.class);

    @Autowired
    private MicroServicesDetail microServicesDetail;

    private List<SellersDTO> sellers;
    private XSSFWorkbook workbook;

    @Override
    public ByteArrayInputStream write(BulkRequest bulkRequest) throws Exception {
        // work book create
        this.workbook = new XSSFWorkbook();
        // create the sheet for work-book
        XSSFSheet publisher_sellers_sheet = workbook.createSheet(PUBLISHERS_SELLERS);
        // stream for write the detail into file
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        //=======================Sheet==========================
        CellStyle cellStyle = this.cellHeadingBackgroundColorStyle(IndexedColors.BLACK.getIndex(), publisher_sellers_sheet);
        Row headerRow = publisher_sellers_sheet.createRow(0);
        this.fillHeading(publisher_sellers_sheet, headerRow, cellStyle, 0, 40*255, PUBLISHERS_SELLERS, DOUBLE_A, true);
        // sub header
        cellStyle = this.cellHeadingBackgroundColorStyle(IndexedColors.BLUE_GREY.getIndex(), publisher_sellers_sheet);
        headerRow = publisher_sellers_sheet.createRow(1);
        this.fillHeading(publisher_sellers_sheet, headerRow, cellStyle, 0, 10*255, ID, null, false);
        this.fillHeading(publisher_sellers_sheet, headerRow, cellStyle, 1, 50*255, NAME, null, false);
        // calling the api for get the data
        this.sellers = this.microServicesDetail.getSellers(bulkRequest.getToken());
        if(this.sellers != null && this.sellers.size() > 0) {
            Integer rows = 2; // start adding data into rows
            CellStyle simple_style = this.cellBodyColorStyle(publisher_sellers_sheet);
            for (SellersDTO seller:  this.sellers) {
                if(seller.getId() != null) {
                    Row row = publisher_sellers_sheet.createRow(rows);
                    this.fillCellValue(0, row, simple_style, seller.getId());
                    this.fillCellValue(1, row, simple_style, seller.getSellerMemberName());
                }
                rows = rows + 1;
            }
        }
        this.workbook.write(out);
        return new ByteArrayInputStream(out.toByteArray());
    }
}
