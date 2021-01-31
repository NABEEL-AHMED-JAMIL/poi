package org.quorum.service.imp;


public abstract class SheetFiledDetailUtil {

    public final String PUBLISHERS_SELLERS = "Publishers-Sellers";
    //====================Service-Filed=================
    public final String ID_KEY = "id";
    public final String DOUBLE_A = "A1:B1";
    public final String DOUBLE_CD = "C1:D1";
    public final String DOUBLE_DE = "D1:E1";
    public final String DOUBLE_E = "E1:F1";
    //=====================Publisher=============
    public final String ID = "Id";
    public final String NAME = "Name";
    //=====================Agency-Advertiser=============
    public final String AGENCY_ADVERTISER = "Agency Advertiser";
    public final String ADVERTISER_ID = "Advertiser Id";
    public final String COMPANY_NAME = "Company Name";
    public final String EMAIL = "Email";
    public final String FIRST_NAME = "First Name";
    public final String LAST_NAME = "Last Name";
    public final String LOGO_URL = "Logo URL";
    public final String COMPANY_WEBSITE = "Company Website";
    //========================Manage-Creative=============
    public final String MANAGE_CREATIVES = "Manage Creatives";
    public final String CREATIVE_ID = "Creative Id";
    //public final String NAME = "Name";
    public final String DIMENTIONS = "Dimentions";
    public final String AUDIT_TYPE = "Audit Type";
    public final String CLICK_URL = "Click URL";
    public final String IMG_URL = "ImgURL";
    public final String LINE_ITEMS = "LineItems";
    //====================Manage-Creative-Header-Detail====
    //public final final String DOUBLE_A = "A1:B1";
    public final String AUDIT_TYPE_NOTE = "NOTE :- Audit Type";
    public final String LINE_ITEMS_NOTE = "NOTE :- Click to view the line-item Id's and use common separate format 1222,2222 for use";
    public final String AUDIT_TYPE_LIST[] = { "Audit", "Self Audit" };
    public final String LINE_ITEM_LIST[] = { "(1233) ABC-Line-Item (Inactive)", "(3456) AD-Line-Item-Pakistan (Active)" };
    //======================Segments-GeoPath=================
    public final String SEGMENTS_GEOPATH = "Segments-GeoPath";
    public static final int SEGMENT_EXPIRY_MAX_DAYS = 91;
    public final String SEGMENT_ID = "Segment Id";
    public final String DESCRIPTION = "Description";
    public final String FLIGHT = "Flight";
    public final String BILLBOARD_IMAGE_URL = "Billboard Image URL";
    public final String GEOPATH_ID = "Geopath Id";
    public final String POIS = "POIs";
    public final String PROCESS_TYPE = "Process Type";
    public final String TOTAL_PREVIOUS_DAY = "Total Previous Days";
    public final String EXPIRY_TYPE = "Expiry Type";
    public final String DEVICE_EXPIRE_DAYS = "Device Expiry Days";
    public final String TIME_ZONE = "Time Zone";
    public final String SEGMENT_GROUP = "Group";
    public final String SEGMENT_FLIGHT = "Segment Flight";
    public final String REGION = "Region";
    //====================Manage-Creative-Header-Detail====
    public final String M = "M";
    public final String METER = "meter";
    public final String FLIGHT_DETAIL  = "Note :- Flight Pattern [startDate=02-11-2019,startTime=00:00:00,endDate=02-11-2019,endTime=00:00:00]";
    public final String TYPE_LIST[] = { "Drive By Location", "Geofence+" };
    public final String PROCESS_TYPE_LIST[] = { "Previous Days", "Exact Date and Time" };
    public final String EXPIRY_TYPE_LIST[] = { "Never Expire", "Device Expiry" };
    public final String TOTAL_PREVIOUS_DAYS_NOTE = "Non Negative Number";
    public final String DEVICE_EXPIRE_DAYS_NOTE = "Less then 31";
    public final String TIME_ZONE_LIST[] = { "PT", "ET", "CT", "MT" };
    public final String ALGO_LIST[] = { "Default", "GeoPath", "Both"};
    //======================Segments-Others=================
    public final String SEGMENTS_OTHERS = "Segments-Others";
    public final String TYPE = "Type";
    public final String FULL_ADDRESS = "Full Address";
    public final String LATITUDE = "Latitude";
    public final String LONGITUDE = "Longitude";
    public final String RADIUS = "Radius";
    public final String RADIUS_UNIT = "Radius Unit";
    public final String GEO_JSON = "GeoJson URL";
    //======================POI==============================
    public final String POI = "POI";
    public final String POI_ID = "POI Id";
    public final String ZIP_CODE = "ZipCode";
    public final String CITY = "City";
    //======================Campaigns==========================
    public final String CAMPAIGNS_STATE[] = { "Inactive" , "Active" };
    public final String CAMPAIGNS_TYPE[] = { "Web" , "Facebook" };
    public final String FB_OBJECTIVE_TYPE[] = { "Traffic" , "Brand Awareness" };
    public final String BUDGET_TYPE_LIST[] = { "Set Budgets" , "Unlimited Budget" };
    public final String BILLING_PERIOD_LIST[] = { "Run my ad set continously starting today" , "Set a start and end date" };
    public final String CAMPAIGNS = "Campaigns";
    public final String CAMPAIGN_ID = "Campaign Id";
    public final String STATE = "State";
    public final String FB_OBJECTIVE = "FB Objective";
    public final String BUDGET_TYPE = "Budget Type";
    public final String BILLING_PERIOD = "Billing Period";
    public final String BUDGET = "Budget";
    public final String START_DATE = "Start Date";
    public final String END_DATE = "End Date";
    //======================Line-Items==============================
    public final String LINE_ITEM = "Line-Items";
    public final String LINE_ITEM_ID = "Lineitem Id";
    public final String GROUP = "Group";
    public final String CATEGORY = "Category";
    public final String BRAND = "Brand";
    public final String ALGO = "Algo";
    public final String GEO_PATH_ID = "GeoPath Id";
    public final String CAMPAIGN = "Campaign";
    public final String SEGMENT = "Segment";
    public final String REVENUE_TYPE = "Revenue Type";
    public final String REVENUE_VALUE = "Revenue Value";
    public final String DAILY_BUDGET = "Daily Budget";
    public final String MIN_BUDGET  = "Min Budget";
    public final String MAX_BUDGET = "Max Budget";
	  public final String CREATIVES = "Creatives";
	  public final String EXCLUDED_PUBLISHERS = "Excluded Publishers";
	  public final String OPTIMIZATION_METHOD = "Optimization Method";
	  public final String OPTIMIZATION_AMOUNT = "Optimization Amount";
	  public final String GOAL_PRIORITY = "Goal Priority";
	  public final String COUNTRY_TARGET = "Country Target";
	  public final String REVENUE_TYPE_LIST[] = { "Cost Plus" };
    public final String LINE_ITEM_BUDGETS_TYPE[] = { "Set Budgets" }; //"Unlimited Budget"
    public final String OPTIMIZATION_METHOD_LIST[] = { "Enable", "Disable" };
    public final String GOAL_PRIORITY_LIST[] = { "Delivery", "Performance" };
    public final String COUNTRY_TARGET_VALUE = "USA";
    public final String SIZE_CREATIVE[] = { "300 x 250", "320 x 50", "300 x 50" };

}
