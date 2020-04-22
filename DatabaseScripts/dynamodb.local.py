import boto3
#ddb = boto3.client('dynamodb', endpoint_url='http://localhost:8043/')
ddb = boto3.client('dynamodb', region_name="us-east-1")
response = ddb.list_tables()
#dynamodb = boto3.resource('dynamodb', region_name='eu-west-1', endpoint_url="http://localhost:8043")
dynamodb = boto3.resource('dynamodb', region_name='us-east-1')
table = dynamodb.Table('requests_data')
table.delete()
print(response)
